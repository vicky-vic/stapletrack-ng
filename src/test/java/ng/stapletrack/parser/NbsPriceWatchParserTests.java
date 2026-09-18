package ng.stapletrack.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ng.stapletrack.entity.ExtremeKind;
import ng.stapletrack.entity.NationalPrice;
import ng.stapletrack.entity.StateExtreme;
import ng.stapletrack.entity.ZonalPrice;
import ng.stapletrack.entity.Zone;

class NbsPriceWatchParserTests {

	private final NbsPriceWatchParser parser = new NbsPriceWatchParser();

	/** The real October 2024 release. data/raw is git-ignored, so these skip when it is absent. */
	@Nested
	class OctoberRelease {

		private static final Path FILE = Path.of("data/raw/selected_food_oct_2024.xlsx");
		private static final YearMonth OCT_2024 = YearMonth.of(2024, 10);

		private ParsedFile parse() throws IOException {
			assumeTrue(Files.exists(FILE), "NBS sample file not present: " + FILE);
			try (InputStream in = Files.newInputStream(FILE)) {
				return parser.parse(in);
			}
		}

		@Test
		void releaseMonthIsNewestAverageColumn() throws IOException {
			assertThat(parse().currentMonth()).isEqualTo(OCT_2024);
		}

		@Test
		void parsesThreeMonthsOfNationalPricesPerItem() throws IOException {
			ParsedFile parsed = parse();
			Map<String, Set<YearMonth>> monthsByItem = parsed.nationalPrices().stream()
					.collect(Collectors.groupingBy(NationalPrice::getItem,
							Collectors.mapping(NationalPrice::getMonthYear, Collectors.toSet())));

			assertThat(monthsByItem).hasSize(43);
			assertThat(monthsByItem.values()).allSatisfy(months -> assertThat(months)
					.containsExactlyInAnyOrder(YearMonth.of(2023, 10), YearMonth.of(2024, 9), OCT_2024));
			assertThat(parsed.nationalPrices()).hasSize(43 * 3);
			assertThat(parsed.warnings()).isEmpty();
		}

		@Test
		void keepsLabelsAndConvertsPricesToKobo() throws IOException {
			NationalPrice eggs = parse().nationalPrices().stream()
					.filter(p -> p.getItem().equals("Agric eggs medium size") && p.getMonthYear().equals(OCT_2024))
					.findFirst().orElseThrow();
			assertThat(eggs.getPrice()).isEqualByComparingTo("2671.60");
		}

		@Test
		void parsesHighestAndLowestStatesForReleaseMonth() throws IOException {
			ParsedFile parsed = parse();
			assertThat(parsed.stateExtremes()).hasSize(43 * 2)
					.allSatisfy(e -> assertThat(e.getMonthYear()).isEqualTo(OCT_2024));

			assertThat(extreme(parsed, "Beans brown,sold loose", ExtremeKind.HIGHEST))
					.extracting(StateExtreme::getState, StateExtreme::getPrice)
					.containsExactly("Bauchi", new BigDecimal("3750.00"));
			assertThat(extreme(parsed, "Beans brown,sold loose", ExtremeKind.LOWEST))
					.extracting(StateExtreme::getState, StateExtreme::getPrice)
					.containsExactly("Yobe", new BigDecimal("1749.52"));
		}

		@Test
		void parsesSixZonesPerItem() throws IOException {
			ParsedFile parsed = parse();
			Map<String, Set<Zone>> zonesByItem = parsed.zonalPrices().stream()
					.collect(Collectors.groupingBy(ZonalPrice::getItem,
							Collectors.mapping(ZonalPrice::getZone, Collectors.toSet())));

			assertThat(zonesByItem).hasSize(43);
			assertThat(zonesByItem.values()).allSatisfy(zones -> assertThat(zones).containsExactlyInAnyOrder(Zone.values()));
			assertThat(parsed.zonalPrices()).allSatisfy(z -> assertThat(z.getMonthYear()).isEqualTo(OCT_2024));

			ZonalPrice eggsNorthCentral = parsed.zonalPrices().stream()
					.filter(z -> z.getItem().equals("Agric eggs medium size") && z.getZone() == Zone.NORTH_CENTRAL)
					.findFirst().orElseThrow();
			assertThat(eggsNorthCentral.getPrice()).isEqualByComparingTo("2915.58");
		}

		private StateExtreme extreme(ParsedFile parsed, String item, ExtremeKind kind) {
			return parsed.stateExtremes().stream()
					.filter(e -> e.getItem().equals(item) && e.getKind() == kind)
					.findFirst().orElseThrow();
		}

	}

	/** Edge cases on small in-memory workbooks — no sample files needed. */
	@Nested
	class SyntheticWorkbooks {

		@Test
		void cleansLabelsAndStateNamesAndSkipsBlankAndTotalRows() {
			Workbook wb = workbook(
					new Object[][] {
							{ "Items Label", "Average of Oct-23", "Average of Sep-24", "Average of Oct-24", "MoM", "Highest", "Lowest" },
							{ "  Rice   local  sold loose ", 800.0, 1500.0, 1600.123, 1.0, "Cross_River (2,100.5)", "Kano (900)" },
							{ null, null, null, null, null, null, null },
							{ "Grand Total", 1.0, 1.0, 1.0, null, null, null } },
					new Object[][] {
							{ "ITEM LABEL", "N/C", "North-East", "NW", "S. East", "SOUTH SOUTH", "South West" },
							{ "Rice local sold loose", 1.0, 2.0, 3.0, 4.0, 5.0, 6.0 } });

			ParsedFile parsed = parser.parse(wb);

			assertThat(parsed.nationalPrices()).extracting(NationalPrice::getItem).containsOnly("Rice local sold loose");
			assertThat(parsed.nationalPrices()).hasSize(3);
			assertThat(parsed.stateExtremes()).extracting(StateExtreme::getState).containsExactly("Cross River", "Kano");
			assertThat(parsed.stateExtremes().getFirst().getPrice()).isEqualByComparingTo("2100.50");
			assertThat(parsed.zonalPrices()).extracting(ZonalPrice::getZone).containsExactlyInAnyOrder(Zone.values());
			assertThat(parsed.warnings()).isEmpty();
		}

		@Test
		void warnsOnMalformedExtremesUnknownZonesAndEmptyRows() {
			Workbook wb = workbook(
					new Object[][] {
							{ "Items Label", "Average of Oct-23", "Average of Sep-24", "Average of Oct-24", "Highest", "Lowest" },
							{ "Yam tuber", 500.0, 1600.0, 0.0, "Kwara 3959", "Adamawa (700)" },
							{ "Tomato", null, null, null, "Abuja (2206)", "Kaduna (734)" } },
					new Object[][] {
							{ "Item Labels", "NORTH CENTRAL", "NORTH EAST", "NORTH WEST", "MIDDLE BELT" },
							{ "Yam tuber", 1700.0, 1650.0, 1600.0, 1800.0 } });

			ParsedFile parsed = parser.parse(wb);

			assertThat(parsed.nationalPrices()).hasSize(2);
			assertThat(parsed.stateExtremes()).extracting(StateExtreme::getState).containsExactly("Adamawa");
			assertThat(parsed.zonalPrices()).hasSize(3);
			assertThat(parsed.warnings())
					.anyMatch(w -> w.contains("Yam tuber") && w.contains("blank or zero"))
					.anyMatch(w -> w.contains("'Kwara 3959'"))
					.anyMatch(w -> w.contains("Tomato") && w.contains("no prices"))
					.anyMatch(w -> w.contains("unrecognised zone header 'MIDDLE BELT'"))
					.anyMatch(w -> w.contains("expected 6 zone columns but found 3"));
		}

		@Test
		void findsSheetsByContentWhenZoneSheetComesFirst() {
			Object[][] summary = {
					{ "ItemLabel", "Average of Dec-22", "Average of Nov-23", "Average of Dec-23", "MoM", "YoY", "Highest", "Lowest" },
					{ "Beans brown,sold loose", 586.14, 838.85, 870.67, 3.8, 48.5, "Akwa_Ibom (1120.92)", "Jigawa (586.04)" },
					{ "Yam tuber", 400.0, 600.0, 650.0, 8.3, 62.5, "Cross_River (900)", "Kano (410)" } };
			Object[][] zones = {
					{ "ITEM LABEL", "NORTH CENTRAL", "NORTH EAST", "NORTH WEST", "SOUTH EAST", "SOUTH SOUTH", "SOUTH WEST" },
					{ "Beans brown,sold loose", 822.57, 731.67, 675.54, 1048.54, 1002.52, 1013.35 },
					{ "Yam tuber", 610.0, 590.0, 580.0, 700.0, 720.0, 690.0 } };

			ParsedFile normal = parser.parse(workbookInOrder(sheet("Selected Food Dec 2023", summary), sheet("Zone all item", zones)));
			ParsedFile reversed = parser.parse(workbookInOrder(sheet("Zone all item", zones), sheet("Selected Food Dec 2023", summary)));

			assertThat(reversed.currentMonth()).isEqualTo(YearMonth.of(2023, 12)).isEqualTo(normal.currentMonth());
			assertThat(reversed.nationalPrices()).hasSize(6);
			assertThat(reversed.zonalPrices()).hasSize(12);
			assertThat(reversed.stateExtremes()).hasSize(4);
			assertThat(reversed.warnings()).isEmpty();
			assertThat(normal.warnings()).isEmpty();

			assertThat(reversed.nationalPrices()).map(p -> p.getItem() + "|" + p.getMonthYear() + "|" + p.getPrice())
					.containsExactlyElementsOf(normal.nationalPrices().stream()
							.map(p -> p.getItem() + "|" + p.getMonthYear() + "|" + p.getPrice()).toList());
			assertThat(reversed.zonalPrices()).map(z -> z.getItem() + "|" + z.getZone() + "|" + z.getPrice())
					.containsExactlyElementsOf(normal.zonalPrices().stream()
							.map(z -> z.getItem() + "|" + z.getZone() + "|" + z.getPrice()).toList());
			assertThat(reversed.stateExtremes()).map(e -> e.getItem() + "|" + e.getKind() + "|" + e.getState() + "|" + e.getPrice())
					.containsExactlyElementsOf(normal.stateExtremes().stream()
							.map(e -> e.getItem() + "|" + e.getKind() + "|" + e.getState() + "|" + e.getPrice()).toList());
		}

		@Test
		void replacesEveryUnderscoreInStateNames() {
			assertThat(NbsPriceWatchParser.cleanStateName("Akwa_Ibom")).isEqualTo("Akwa Ibom");
			assertThat(NbsPriceWatchParser.cleanStateName("Cross_River")).isEqualTo("Cross River");
			assertThat(NbsPriceWatchParser.cleanStateName(" Federal__Capital_Territory ")).isEqualTo("Federal Capital Territory");
		}

		@Test
		void acceptsItemHeaderVariantsAndFindsItemColumnByHeader() {
			for (String header : List.of("ITEM LABEL", "Item Label", "ItemLabel", "Item labels", "Items Label", "Items")) {
				ParsedFile parsed = parser.parse(workbook(
						new Object[][] {
								{ "Average of Sep-24", "Average of Oct-24", header },
								{ 1500.0, 1600.0, "Rice local sold loose" } },
						null));
				assertThat(parsed.nationalPrices()).as(header).extracting(NationalPrice::getItem)
						.containsOnly("Rice local sold loose");
				assertThat(parsed.warnings()).as(header).noneMatch(w -> w.contains("Item Label"));
			}
		}

		@Test
		void rejectsWorkbookWithoutMonthColumns() {
			Workbook wb = workbook(new Object[][] { { "Name", "Score" }, { "Ada", 10.0 } }, null);
			assertThatThrownBy(() -> parser.parse(wb))
					.isInstanceOf(NbsFormatException.class)
					.hasMessageContaining("Average of MMM-yy");
		}

		@Test
		void rejectsNonWorkbookBytes() {
			InputStream notExcel = new ByteArrayInputStream("item,price\nrice,100\n".getBytes());
			assertThatThrownBy(() -> parser.parse(notExcel))
					.isInstanceOf(NbsFormatException.class)
					.hasMessageContaining("could not be read");
		}

		private static Workbook workbook(Object[][] priceRows, Object[][] zoneRows) {
			return zoneRows == null
					? workbookInOrder(sheet("Selected Food oct 2024", priceRows))
					: workbookInOrder(sheet("Selected Food oct 2024", priceRows), sheet("ZONE all item", zoneRows));
		}

		private record SheetSpec(String name, Object[][] rows) {
		}

		private static SheetSpec sheet(String name, Object[][] rows) {
			return new SheetSpec(name, rows);
		}

		/** Sheets are created in argument order, so tab position can be controlled. */
		private static Workbook workbookInOrder(SheetSpec... sheets) {
			Workbook wb = new XSSFWorkbook();
			for (SheetSpec spec : sheets) {
				fill(wb.createSheet(spec.name()), spec.rows());
			}
			return wb;
		}

		private static void fill(Sheet sheet, Object[][] rows) {
			for (int r = 0; r < rows.length; r++) {
				Row row = sheet.createRow(r);
				for (int c = 0; c < rows[r].length; c++) {
					Object value = rows[r][c];
					if (value instanceof String s) {
						row.createCell(c).setCellValue(s);
					}
					else if (value instanceof Double d) {
						row.createCell(c).setCellValue(d);
					}
				}
			}
		}

	}

}
