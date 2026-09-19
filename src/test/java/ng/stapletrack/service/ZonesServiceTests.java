package ng.stapletrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

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

	private static ZonalPrice zonal(String item, YearMonth month, Zone zone, String price) {
		return new ZonalPrice(item, month, zone, new BigDecimal(price));
	}

}
