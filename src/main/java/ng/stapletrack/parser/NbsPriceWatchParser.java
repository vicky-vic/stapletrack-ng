package ng.stapletrack.parser;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import ng.stapletrack.entity.ExtremeKind;
import ng.stapletrack.entity.NationalPrice;
import ng.stapletrack.entity.StateExtreme;
import ng.stapletrack.entity.ZonalPrice;
import ng.stapletrack.entity.Zone;

/**
 * Reads one monthly NBS "Selected Food Prices Watch" workbook.
 *
 * <p>Sheets are identified by their header row, never by position or name, because both vary
 * between releases (Dec 2023 puts the zone sheet first; Nov 2023 calls the summary "National"):
 * <ul>
 * <li>summary sheet — header has "Average of MMM-yy" columns plus "Highest"/"Lowest" cells such as "Bauchi (3750)";
 * <li>zonal sheet — header has at least three geopolitical zone names.
 * </ul>
 * Within a sheet, columns are likewise located by header text.
 */
@Component
public class NbsPriceWatchParser {

	private static final Pattern MONTH_HEADER = Pattern.compile("Average of ([A-Z][a-z]{2})-(\\d{2})",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern EXTREME_CELL = Pattern.compile("^(.+?)\\s*\\(([\\d,.]+)\\)$");
	private static final Set<String> ZONE_HEADERS = Set.of("north central", "north east", "north west", "south east",
			"south south", "south west");
	/** Normalised item-column headers: "ITEM LABEL", "ItemLabel", "Items Label", "Item labels", "Items"… */
	private static final Set<String> ITEM_HEADERS = Set.of("itemlabel", "itemlabels", "itemslabel", "item", "items");
	private static final int MIN_ZONE_HEADERS = 3;
	private static final int HEADER_SCAN_ROWS = 10;
	private static final int EXPECTED_MONTH_COLUMNS = 3;
	private static final int MAX_ITEM_LENGTH = 120;
	private static final int MAX_STATE_LENGTH = 40;

	private enum SheetKind { SUMMARY, ZONAL }

	/** A sheet recognised by its header row. */
	private record ClassifiedSheet(SheetKind kind, Sheet sheet, Row header) {
	}

	public ParsedFile parse(InputStream in) {
		try (Workbook workbook = WorkbookFactory.create(in)) {
			return parse(workbook);
		}
		catch (NbsFormatException e) {
			throw e;
		}
		catch (IOException | RuntimeException e) {
			throw new NbsFormatException("The file could not be read as an Excel workbook (.xlsx).", e);
		}
	}

	ParsedFile parse(Workbook workbook) {
		List<String> warnings = new ArrayList<>();

		ClassifiedSheet summary = null;
		ClassifiedSheet zonal = null;
		for (Sheet sheet : workbook) {
			Optional<ClassifiedSheet> classified = classify(sheet);
			if (classified.isEmpty()) {
				continue;
			}
			ClassifiedSheet found = classified.get();
			if (found.kind() == SheetKind.SUMMARY && summary == null) {
				summary = found;
			}
			else if (found.kind() == SheetKind.ZONAL && zonal == null) {
				zonal = found;
			}
			else {
				warnings.add("Sheet '" + sheet.getSheetName() + "' looks like a second "
						+ found.kind().name().toLowerCase(Locale.ROOT) + " sheet, ignored.");
			}
		}
		if (summary == null) {
			throw new NbsFormatException("No sheet has 'Average of MMM-yy' columns"
					+ " — is this an NBS Selected Food Prices Watch file?");
		}

		SummaryResult national = parseSummarySheet(summary, warnings);
		List<ZonalPrice> zonalPrices;
		if (zonal == null) {
			warnings.add("No sheet has geopolitical zone columns — zonal prices were not imported.");
			zonalPrices = List.of();
		}
		else {
			zonalPrices = parseZoneSheet(zonal, national.currentMonth(), warnings);
		}
		return new ParsedFile(national.currentMonth(), national.prices(), national.extremes(), zonalPrices, warnings);
	}

	/**
	 * Scans the first rows of a sheet for a header: a row with an "Average of MMM-yy" cell makes it the
	 * summary sheet; a row with at least three zone names makes it the zonal sheet.
	 */
	private static Optional<ClassifiedSheet> classify(Sheet sheet) {
		int lastRow = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + HEADER_SCAN_ROWS - 1);
		for (int r = Math.max(sheet.getFirstRowNum(), 0); r <= lastRow; r++) {
			Row row = sheet.getRow(r);
			if (row == null) {
				continue;
			}
			int zoneCells = 0;
			for (Cell cell : row) {
				String text = text(cell);
				if (MONTH_HEADER.matcher(text).find()) {
					return Optional.of(new ClassifiedSheet(SheetKind.SUMMARY, sheet, row));
				}
				if (ZONE_HEADERS.contains(zoneKey(text))) {
					zoneCells++;
				}
			}
			if (zoneCells >= MIN_ZONE_HEADERS) {
				return Optional.of(new ClassifiedSheet(SheetKind.ZONAL, sheet, row));
			}
		}
		return Optional.empty();
	}

	private record SummaryResult(YearMonth currentMonth, List<NationalPrice> prices, List<StateExtreme> extremes) {
	}

	private SummaryResult parseSummarySheet(ClassifiedSheet summary, List<String> warnings) {
		Sheet sheet = summary.sheet();
		Row header = summary.header();
		String sheetName = sheet.getSheetName();

		Map<Integer, YearMonth> monthColumns = new LinkedHashMap<>();
		Integer highestCol = null;
		Integer lowestCol = null;
		for (Cell cell : header) {
			String text = text(cell);
			Matcher m = MONTH_HEADER.matcher(text);
			if (m.find()) {
				parseMonth(m.group(1), m.group(2)).ifPresentOrElse(
						month -> monthColumns.put(cell.getColumnIndex(), month),
						() -> warnings.add("Sheet '" + sheetName + "': unrecognised month header '" + text + "'."));
			}
			else if (text.equalsIgnoreCase("Highest")) {
				highestCol = cell.getColumnIndex();
			}
			else if (text.equalsIgnoreCase("Lowest")) {
				lowestCol = cell.getColumnIndex();
			}
		}
		if (monthColumns.isEmpty()) {
			throw new NbsFormatException("Sheet '" + sheetName
					+ "' has no readable 'Average of MMM-yy' columns — is this an NBS Selected Food Prices Watch file?");
		}
		if (monthColumns.size() != EXPECTED_MONTH_COLUMNS) {
			warnings.add("Expected " + EXPECTED_MONTH_COLUMNS + " 'Average of' month columns but found "
					+ monthColumns.size() + ".");
		}
		if (highestCol == null || lowestCol == null) {
			warnings.add("Sheet '" + sheetName
					+ "' is missing a 'Highest' or 'Lowest' column — state extremes were not imported.");
		}
		YearMonth currentMonth = monthColumns.values().stream().max(Comparator.naturalOrder()).orElseThrow();
		int itemCol = itemColumn(sheetName, header, warnings);

		List<NationalPrice> national = new ArrayList<>();
		List<StateExtreme> extremes = new ArrayList<>();
		Set<String> seenItems = new HashSet<>();
		for (Row row : sheet) {
			if (row.getRowNum() <= header.getRowNum()) {
				continue;
			}
			String where = "Sheet '" + sheetName + "' row " + (row.getRowNum() + 1);
			String item = itemLabel(row, itemCol, where, seenItems, warnings);
			if (item == null) {
				continue;
			}

			List<NationalPrice> rowPrices = new ArrayList<>();
			for (Map.Entry<Integer, YearMonth> column : monthColumns.entrySet()) {
				BigDecimal price = price(row.getCell(column.getKey()));
				if (price != null) {
					rowPrices.add(new NationalPrice(item, column.getValue(), price));
				}
			}
			if (rowPrices.isEmpty()) {
				warnings.add(where + " (" + item + "): no prices, skipped.");
				continue;
			}
			if (rowPrices.size() < monthColumns.size()) {
				warnings.add(where + " (" + item + "): " + (monthColumns.size() - rowPrices.size())
						+ " month(s) blank or zero, skipped those cells.");
			}
			national.addAll(rowPrices);

			if (highestCol != null && lowestCol != null) {
				extreme(row.getCell(highestCol), item, currentMonth, ExtremeKind.HIGHEST, where, warnings)
						.ifPresent(extremes::add);
				extreme(row.getCell(lowestCol), item, currentMonth, ExtremeKind.LOWEST, where, warnings)
						.ifPresent(extremes::add);
			}
		}
		return new SummaryResult(currentMonth, national, extremes);
	}

	private List<ZonalPrice> parseZoneSheet(ClassifiedSheet zonalSheet, YearMonth currentMonth, List<String> warnings) {
		Sheet sheet = zonalSheet.sheet();
		Row header = zonalSheet.header();
		String sheetName = sheet.getSheetName();
		int itemCol = itemColumn(sheetName, header, warnings);

		Map<Integer, Zone> zoneColumns = new LinkedHashMap<>();
		Map<Zone, Integer> columnOfZone = new EnumMap<>(Zone.class);
		for (Cell cell : header) {
			if (cell.getColumnIndex() == itemCol) {
				continue;
			}
			String text = text(cell);
			if (text.isEmpty()) {
				continue;
			}
			Optional<Zone> zone = Zone.fromNbsLabel(text);
			if (zone.isEmpty()) {
				warnings.add("Sheet '" + sheetName + "': unrecognised zone header '" + text + "', column ignored.");
			}
			else if (columnOfZone.containsKey(zone.get())) {
				warnings.add("Sheet '" + sheetName + "': zone '" + text + "' appears twice, using the first column.");
			}
			else {
				zoneColumns.put(cell.getColumnIndex(), zone.get());
				columnOfZone.put(zone.get(), cell.getColumnIndex());
			}
		}
		if (zoneColumns.size() != Zone.values().length) {
			warnings.add("Sheet '" + sheetName + "': expected " + Zone.values().length + " zone columns but found "
					+ zoneColumns.size() + ".");
		}

		List<ZonalPrice> zonal = new ArrayList<>();
		Set<String> seenItems = new HashSet<>();
		for (Row row : sheet) {
			if (row.getRowNum() <= header.getRowNum()) {
				continue;
			}
			String where = "Sheet '" + sheetName + "' row " + (row.getRowNum() + 1);
			String item = itemLabel(row, itemCol, where, seenItems, warnings);
			if (item == null) {
				continue;
			}
			List<ZonalPrice> rowPrices = new ArrayList<>();
			for (Map.Entry<Integer, Zone> column : zoneColumns.entrySet()) {
				BigDecimal price = price(row.getCell(column.getKey()));
				if (price != null) {
					rowPrices.add(new ZonalPrice(item, currentMonth, column.getValue(), price));
				}
			}
			if (rowPrices.isEmpty()) {
				warnings.add(where + " (" + item + "): no zone prices, skipped.");
				continue;
			}
			if (rowPrices.size() < zoneColumns.size()) {
				warnings.add(where + " (" + item + "): " + (zoneColumns.size() - rowPrices.size())
						+ " zone(s) blank or zero, skipped those cells.");
			}
			zonal.addAll(rowPrices);
		}
		return zonal;
	}

	/** The column whose header is an item label (see {@link #ITEM_HEADERS}); falls back to column A. */
	private static int itemColumn(String sheetName, Row header, List<String> warnings) {
		for (Cell cell : header) {
			String key = text(cell).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
			if (ITEM_HEADERS.contains(key)) {
				return cell.getColumnIndex();
			}
		}
		warnings.add("Sheet '" + sheetName + "': no 'Item Label' header found, reading item names from column A.");
		return 0;
	}

	/**
	 * The item column, cleaned up; null means "skip this row". Blank rows and "Grand Total"
	 * are skipped silently; duplicates and over-long labels produce a warning.
	 */
	private static String itemLabel(Row row, int itemCol, String where, Set<String> seenItems, List<String> warnings) {
		String item = cleanLabel(text(row.getCell(itemCol)));
		if (item.isEmpty() || item.equalsIgnoreCase("Grand Total")) {
			return null;
		}
		if (item.length() > MAX_ITEM_LENGTH) {
			warnings.add(where + ": item label longer than " + MAX_ITEM_LENGTH + " characters, skipped.");
			return null;
		}
		if (!seenItems.add(item)) {
			warnings.add(where + " (" + item + "): duplicate item, skipped.");
			return null;
		}
		return item;
	}

	private static Optional<StateExtreme> extreme(Cell cell, String item, YearMonth month, ExtremeKind kind,
			String where, List<String> warnings) {
		String text = text(cell);
		String label = kind == ExtremeKind.HIGHEST ? "Highest" : "Lowest";
		if (text.isEmpty()) {
			warnings.add(where + " (" + item + "): " + label + " is blank, skipped.");
			return Optional.empty();
		}
		Matcher m = EXTREME_CELL.matcher(text);
		BigDecimal price = m.matches() ? parseNumber(m.group(2)) : null;
		if (price == null) {
			warnings.add(where + " (" + item + "): " + label + " '" + text
					+ "' is not in the form 'State (price)', skipped.");
			return Optional.empty();
		}
		String state = cleanStateName(m.group(1));
		if (state.length() > MAX_STATE_LENGTH) {
			warnings.add(where + " (" + item + "): " + label + " state name too long, skipped.");
			return Optional.empty();
		}
		return Optional.of(new StateExtreme(item, month, kind, state, price));
	}

	/** NBS writes multi-word states with underscores in some releases: "Akwa_Ibom" → "Akwa Ibom". */
	static String cleanStateName(String raw) {
		return cleanLabel(raw.replaceAll("_+", " "));
	}

	/** Trim, turn non-breaking spaces into spaces and collapse runs of whitespace; casing is kept. */
	static String cleanLabel(String raw) {
		return raw.replace(' ', ' ').trim().replaceAll("\\s+", " ");
	}

	/** Lower-case, punctuation to spaces, whitespace collapsed: "NORTH-CENTRAL " → "north central". */
	private static String zoneKey(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").trim();
	}

	static Optional<YearMonth> parseMonth(String monthAbbrev, String twoDigitYear) {
		for (Month month : Month.values()) {
			if (month.name().substring(0, 3).equalsIgnoreCase(monthAbbrev)) {
				return Optional.of(YearMonth.of(2000 + Integer.parseInt(twoDigitYear), month));
			}
		}
		return Optional.empty();
	}

	/** Positive price rounded to kobo, or null when the cell is blank, zero, negative or not a number. */
	private static BigDecimal price(Cell cell) {
		if (cell == null) {
			return null;
		}
		CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
		BigDecimal value = switch (type) {
			case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
			case STRING -> parseNumber(cell.getStringCellValue());
			default -> null;
		};
		if (value == null || value.signum() <= 0) {
			return null;
		}
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal parseNumber(String text) {
		String cleaned = text.replace(",", "").trim();
		if (cleaned.isEmpty()) {
			return null;
		}
		try {
			BigDecimal value = new BigDecimal(cleaned);
			return value.signum() > 0 ? value.setScale(2, RoundingMode.HALF_UP) : null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/** Cell contents as trimmed text; numbers are rendered without a trailing ".0". */
	private static String text(Cell cell) {
		if (cell == null) {
			return "";
		}
		CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
		return switch (type) {
			case STRING -> cell.getStringCellValue().trim();
			case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
			case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
			default -> "";
		};
	}

}
