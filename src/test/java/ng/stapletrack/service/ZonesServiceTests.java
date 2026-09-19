package ng.stapletrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ng.stapletrack.entity.ZonalPrice;
import ng.stapletrack.entity.Zone;
import ng.stapletrack.repository.ZonalPriceRepository;
import ng.stapletrack.service.ZonesService.ZonalBreakdown;
import ng.stapletrack.service.ZonesService.ZonePrice;

class ZonesServiceTests {

	private static final String RICE = "Rice local sold loose";
	private static final String TOMATO = "Tomato";
	private static final YearMonth OCT_2024 = YearMonth.of(2024, 10);
	private static final YearMonth FEB_2024 = YearMonth.of(2024, 2);

	private ZonalPriceRepository repository;
	private ZonesService service;

	@BeforeEach
	void setUp() {
		repository = mock(ZonalPriceRepository.class);
		when(repository.findByItemAndMonthYearOrderByPriceAsc(any(), any())).thenReturn(List.of());
		service = new ZonesService(repository);
	}

	@Test
	void keepsCheapestFirstOrderAndIdentifiesExtremes() {
		// Repository contract: ordered by price ascending
		when(repository.findByItemAndMonthYearOrderByPriceAsc(RICE, OCT_2024)).thenReturn(List.of(
				zonal(RICE, OCT_2024, Zone.NORTH_WEST, "1780.10"),
				zonal(RICE, OCT_2024, Zone.NORTH_EAST, "1832.00"),
				zonal(RICE, OCT_2024, Zone.NORTH_CENTRAL, "1901.55"),
				zonal(RICE, OCT_2024, Zone.SOUTH_WEST, "1966.40"),
				zonal(RICE, OCT_2024, Zone.SOUTH_EAST, "2010.00"),
				zonal(RICE, OCT_2024, Zone.SOUTH_SOUTH, "2083.72")));

		ZonalBreakdown result = service.findZonalBreakdown(RICE, OCT_2024).orElseThrow();

		assertThat(result.item()).isEqualTo(RICE);
		assertThat(result.month()).isEqualTo(OCT_2024);
		assertThat(result.zones()).extracting(ZonePrice::name).containsExactly(
				"North West", "North East", "North Central", "South West", "South East", "South South");
		assertThat(result.zones()).extracting(ZonePrice::price).isSortedAccordingTo(BigDecimal::compareTo);
		assertThat(result.cheapest().zone()).isEqualTo(Zone.NORTH_WEST);
		assertThat(result.cheapest().price()).isEqualByComparingTo("1780.10");
		assertThat(result.priciest().zone()).isEqualTo(Zone.SOUTH_SOUTH);
		assertThat(result.priciest().name()).isEqualTo("South South");
		assertThat(result.priciest().price()).isEqualByComparingTo("2083.72");
	}

	@Test
	void narrowSpreadReadsAsPercentage() {
		// 2083.72 / 1780.10 = +17.06% → "17% more"
		when(repository.findByItemAndMonthYearOrderByPriceAsc(RICE, OCT_2024)).thenReturn(List.of(
				zonal(RICE, OCT_2024, Zone.NORTH_WEST, "1780.10"),
				zonal(RICE, OCT_2024, Zone.SOUTH_SOUTH, "2083.72")));

		ZonalBreakdown result = service.findZonalBreakdown(RICE, OCT_2024).orElseThrow();

		assertThat(result.spread().label()).isEqualTo("17% more");
		assertThat(result.spread().shortLabel()).isEqualTo("17%");
	}

	@Test
	void wideSpreadReadsAsMultiple() {
		// 1414.73 / 566.96 = 2.495… → "2.5×"
		when(repository.findByItemAndMonthYearOrderByPriceAsc(TOMATO, FEB_2024)).thenReturn(List.of(
				zonal(TOMATO, FEB_2024, Zone.NORTH_WEST, "566.96"),
				zonal(TOMATO, FEB_2024, Zone.NORTH_EAST, "688.19"),
				zonal(TOMATO, FEB_2024, Zone.SOUTH_SOUTH, "1414.73")));

		ZonalBreakdown result = service.findZonalBreakdown(TOMATO, FEB_2024).orElseThrow();

		assertThat(result.spread().multiple()).isTrue();
		assertThat(result.spread().label()).isEqualTo("2.5×");
		assertThat(result.spread().shortLabel()).isEqualTo("2.5×");
	}

	@Test
	void emptyWhenNoOrOnlyOneZoneHasData() {
		assertThat(service.findZonalBreakdown(RICE, OCT_2024)).isEmpty();

		when(repository.findByItemAndMonthYearOrderByPriceAsc(RICE, FEB_2024))
				.thenReturn(List.of(zonal(RICE, FEB_2024, Zone.NORTH_WEST, "1200")));
		assertThat(service.findZonalBreakdown(RICE, FEB_2024)).isEmpty();
	}

	@Test
	void hexColorsRunFromGreenThroughToAmberByPriceRank() {
		List<ZonePrice> sorted = List.of(
				price(Zone.NORTH_WEST, "1763.62"), price(Zone.NORTH_CENTRAL, "1902.82"), price(Zone.NORTH_EAST, "1930.70"),
				price(Zone.SOUTH_SOUTH, "1984.30"), price(Zone.SOUTH_WEST, "2011.05"), price(Zone.SOUTH_EAST, "2146.08"));

		Map<Zone, String> colors = ZonesService.computeHexColors(sorted);

		assertThat(colors).hasSize(6);
		assertThat(colors.get(Zone.NORTH_WEST)).isEqualTo(ZonesService.CHEAPEST_COLOR);
		assertThat(colors.get(Zone.SOUTH_EAST)).isEqualTo(ZonesService.PRICIEST_COLOR);
		// Hue falls monotonically from green (~155°) to amber (~38°) as price rank rises
		List<Double> hues = sorted.stream().map(z -> ZonesService.hue(colors.get(z.zone()))).toList();
		assertThat(hues.getFirst()).isBetween(140.0, 165.0);
		assertThat(hues.getLast()).isBetween(30.0, 45.0);
		assertThat(hues).isSortedAccordingTo(Comparator.reverseOrder());
		assertThat(hues).doesNotHaveDuplicates();
		assertThat(colors.values()).allMatch(c -> c.matches("#[0-9A-F]{6}"));
	}

	@Test
	void zonesWithoutDataAreLeftOffTheMap() {
		// Only four zones reported a price
		when(repository.findByItemAndMonthYearOrderByPriceAsc(RICE, OCT_2024)).thenReturn(List.of(
				zonal(RICE, OCT_2024, Zone.NORTH_WEST, "1780"),
				zonal(RICE, OCT_2024, Zone.NORTH_EAST, "1830"),
				zonal(RICE, OCT_2024, Zone.SOUTH_WEST, "1960"),
				zonal(RICE, OCT_2024, Zone.SOUTH_EAST, "2010")));

		ZonalBreakdown result = service.findZonalBreakdown(RICE, OCT_2024).orElseThrow();

		assertThat(result.hexColors()).containsOnlyKeys(Zone.NORTH_WEST, Zone.NORTH_EAST, Zone.SOUTH_WEST, Zone.SOUTH_EAST);
		assertThat(result.hexColors().get(Zone.NORTH_WEST)).isEqualTo(ZonesService.CHEAPEST_COLOR);
		assertThat(result.hexColors().get(Zone.SOUTH_EAST)).isEqualTo(ZonesService.PRICIEST_COLOR);
		// Two middle zones sit at 1/3 and 2/3 of the ramp
		assertThat(ZonesService.hue(result.hexColors().get(Zone.NORTH_EAST)))
				.isGreaterThan(ZonesService.hue(result.hexColors().get(Zone.SOUTH_WEST)));
	}

	private static ZonePrice price(Zone zone, String price) {
		return new ZonePrice(zone, Zones.displayName(zone), new BigDecimal(price));
	}

	private static ZonalPrice zonal(String item, YearMonth month, Zone zone, String price) {
		return new ZonalPrice(item, month, zone, new BigDecimal(price));
	}

}
