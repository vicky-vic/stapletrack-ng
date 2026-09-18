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
							{ "Item Labels", "NORTH CENTRAL", "MIDDLE BELT" },
							{ "Yam tuber", 1700.0, 1800.0 } });

			ParsedFile parsed = parser.parse(wb);

			assertThat(parsed.nationalPrices()).hasSize(2);
			assertThat(parsed.stateExtremes()).extracting(StateExtreme::getState).containsExactly("Adamawa");
			assertThat(parsed.zonalPrices()).hasSize(1);
			assertThat(parsed.warnings())
					.anyMatch(w -> w.contains("Yam tuber") && w.contains("blank or zero"))
					.anyMatch(w -> w.contains("'Kwara 3959'"))
					.anyMatch(w -> w.contains("Tomato") && w.contains("no prices"))
					.anyMatch(w -> w.contains("unrecognised zone header 'MIDDLE BELT'"))
					.anyMatch(w -> w.contains("expected 6 zone columns but found 1"));
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
			Workbook wb = new XSSFWorkbook();
			fill(wb.createSheet("Selected Food oct 2024"), priceRows);
			if (zoneRows != null) {
				fill(wb.createSheet("ZONE all item"), zoneRows);
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
