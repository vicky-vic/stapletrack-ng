package ng.stapletrack.service.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ng.stapletrack.entity.NationalPrice;
import ng.stapletrack.repository.NationalPriceRepository;
import ng.stapletrack.service.forecast.ForecastResult.ForecastPoint;
import ng.stapletrack.service.forecast.LinearRegression.Fit;
import ng.stapletrack.service.forecast.LinearRegression.Prediction;

class ForecastServiceTests {

	private static final String RICE = "Rice local sold loose";
	private static final YearMonth START = YearMonth.of(2024, 1);
	/** Roughly 1000 + 50x with a little noise, so the band has non-zero width. */
	private static final double[] PRICES = { 1003, 1046, 1104, 1149, 1197, 1256 };

	private NationalPriceRepository repository;
	private ForecastService service;

	@BeforeEach
	void setUp() {
		repository = mock(NationalPriceRepository.class);
		service = new ForecastService(repository);
	}

	@Test
	void predictionsMatchLinearRegression() {
		when(repository.findByItemOrderByMonthYearAsc(RICE)).thenReturn(rows(START, PRICES));

		ForecastResult result = service.forecast(RICE, 3).orElseThrow();

		List<Double> xs = List.of(0.0, 1.0, 2.0, 3.0, 4.0, 5.0);
		List<Double> ys = new ArrayList<>();
		for (double p : PRICES) {
			ys.add(p);
		}
		Fit fit = LinearRegression.fit(xs, ys);

		assertThat(result.forecast()).extracting(ForecastPoint::month)
				.containsExactly(YearMonth.of(2024, 7), YearMonth.of(2024, 8), YearMonth.of(2024, 9));
		for (int k = 1; k <= 3; k++) {
			Prediction expected = LinearRegression.predict(fit, 5 + k, 1.96);
			ForecastPoint actual = result.forecast().get(k - 1);
			assertThat(actual.pointEstimate().doubleValue()).isCloseTo(expected.pointEstimate(), within(0.005));
			assertThat(actual.lower().doubleValue()).isCloseTo(expected.lower(), within(0.005));
			assertThat(actual.upper().doubleValue()).isCloseTo(expected.upper(), within(0.005));
		}

		assertThat(result.history()).hasSize(6);
		assertThat(result.meta().n()).isEqualTo(6);
		assertThat(result.meta().rSquared()).isBetween(0.99, 1.0);
		assertThat(result.meta().sampleWindow()).isEqualTo("Jan 2024 – Jun 2024");
		assertThat(result.meta().fitDescription()).isEqualTo("strong linear trend");
	}

	@Test
	void keepsRealSpacingAcrossMissingMonths() {
		// Jan, Feb, Mar, then a gap, then Jun: x = 0, 1, 2, 5
		List<NationalPrice> rows = new ArrayList<>(rows(START, new double[] { 100, 110, 120 }));
		rows.add(new NationalPrice(RICE, YearMonth.of(2024, 6), new BigDecimal("150")));
		when(repository.findByItemOrderByMonthYearAsc(RICE)).thenReturn(rows);

		ForecastResult result = service.forecast(RICE, 1).orElseThrow();

		// Points lie exactly on y = 100 + 10x, so July (x = 6) is 160 with no spread
		ForecastPoint july = result.nextMonth();
		assertThat(july.month()).isEqualTo(YearMonth.of(2024, 7));
		assertThat(july.pointEstimate()).isEqualByComparingTo("160.00");
		assertThat(july.lower()).isEqualByComparingTo("160.00");
		assertThat(result.meta().rSquared()).isCloseTo(1.0, within(1e-9));
	}

	@Test
	void rSquaredMatchesHandComputedValue() {
		// ys 2,4,5,4,5 on x 0..4: SSE 2.4, SST 6 → R² = 0.6
		Fit fit = LinearRegression.fit(List.of(0.0, 1.0, 2.0, 3.0, 4.0), List.of(2.0, 4.0, 5.0, 4.0, 5.0));
		assertThat(ForecastService.rSquared(fit, List.of(0.0, 1.0, 2.0, 3.0, 4.0), List.of(2.0, 4.0, 5.0, 4.0, 5.0)))
				.isCloseTo(0.6, within(1e-9));
	}

	@Test
	void emptyWithFewerThanThreePoints() {
		when(repository.findByItemOrderByMonthYearAsc(RICE)).thenReturn(rows(START, new double[] { 100, 110 }));
		assertThat(service.forecast(RICE, 3)).isEmpty();
	}

	@Test
	void fullForecastIsLabelledWithItsLength() {
		when(repository.findByItemOrderByMonthYearAsc(RICE)).thenReturn(rows(START, PRICES));
		assertThat(service.forecast(RICE, 3).orElseThrow().meta().windowLabel()).isEqualTo("Full history (6 months)");
	}

	@Test
	void forecastRecentUsesOnlyTheLastTwelveMonths() {
		// 24 months, Jan 2023 – Dec 2024: flat at 100 for a year, then rising 10/month
		double[] prices = new double[24];
		for (int i = 0; i < 24; i++) {
			prices[i] = i < 12 ? 100 : 100 + 10 * (i - 11);
		}
		when(repository.findByItemOrderByMonthYearAsc(RICE)).thenReturn(rows(YearMonth.of(2023, 1), prices));

		ForecastResult recent = service.forecastRecent(RICE, 3).orElseThrow();
		ForecastResult full = service.forecast(RICE, 3).orElseThrow();

		assertThat(recent.meta().n()).isEqualTo(12);
		assertThat(recent.meta().windowLabel()).isEqualTo("Recent trend (last 12 months)");
		assertThat(recent.meta().sampleWindow()).isEqualTo("Jan 2024 – Dec 2024");
		assertThat(recent.history()).extracting(ForecastResult.PricePoint::month)
				.startsWith(YearMonth.of(2024, 1)).endsWith(YearMonth.of(2024, 12)).hasSize(12);
		assertThat(full.meta().n()).isEqualTo(24);

		// Must equal a fit on just the last 12 points
		List<Double> ys = new ArrayList<>();
		for (int i = 12; i < 24; i++) {
			ys.add(prices[i]);
		}
		Fit expected = LinearRegression.fit(List.of(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0), ys);
		for (int k = 1; k <= 3; k++) {
			Prediction p = LinearRegression.predict(expected, 11 + k, 1.96);
			assertThat(recent.forecast().get(k - 1).pointEstimate().doubleValue()).isCloseTo(p.pointEstimate(), within(0.005));
		}
		assertThat(recent.nextMonth().month()).isEqualTo(YearMonth.of(2025, 1));
		assertThat(recent.nextMonth().pointEstimate()).isEqualByComparingTo("230.00");

		// The full window is dragged down by the flat year and starts below the latest price (220)
		assertThat(full.nextMonth().pointEstimate()).isLessThan(new BigDecimal("220"));
	}

	@Test
	void forecastRecentUsesWindowByCalendarMonthsNotPointCount() {
		// 14 points spanning 16 months (two missing): only points within the last 12 calendar months count
		List<NationalPrice> rows = new ArrayList<>();
		YearMonth start = YearMonth.of(2023, 1);
		for (int i = 0; i < 16; i++) {
			if (i == 5 || i == 9) {
				continue;
			}
			rows.add(new NationalPrice(RICE, start.plusMonths(i), BigDecimal.valueOf(100 + 5 * i)));
		}
		when(repository.findByItemOrderByMonthYearAsc(RICE)).thenReturn(rows);

		ForecastResult recent = service.forecastRecent(RICE, 1).orElseThrow();

		// Window May 2023 – Apr 2024 (months 4..15) minus the two gaps = 10 points
		assertThat(recent.meta().n()).isEqualTo(10);
		assertThat(recent.meta().sampleWindow()).isEqualTo("May 2023 – Apr 2024");
	}

	@Test
	void forecastRecentUsesAllDataWhenHistoryIsShort() {
		when(repository.findByItemOrderByMonthYearAsc(RICE)).thenReturn(rows(START, PRICES));

		ForecastResult recent = service.forecastRecent(RICE, 3).orElseThrow();
		ForecastResult full = service.forecast(RICE, 3).orElseThrow();

		assertThat(recent.meta().n()).isEqualTo(6);
		assertThat(recent.meta().windowLabel()).isEqualTo("Recent trend (all 6 months)");
		assertThat(recent.forecast()).isEqualTo(full.forecast());
	}

	@Test
	void fitDescriptionThresholds() {
		assertThat(meta(0.9).fitDescription()).isEqualTo("strong linear trend");
		assertThat(meta(0.7).fitDescription()).isEqualTo("moderate linear trend");
		assertThat(meta(0.4).fitDescription()).isEqualTo("weak — take with caution");
	}

	private static ForecastResult.ForecastMeta meta(double rSquared) {
		return new ForecastResult.ForecastMeta(10, rSquared, "", "");
	}

	private static List<NationalPrice> rows(YearMonth start, double[] prices) {
		List<NationalPrice> rows = new ArrayList<>();
		for (int i = 0; i < prices.length; i++) {
			rows.add(new NationalPrice(RICE, start.plusMonths(i), BigDecimal.valueOf(prices[i])));
		}
		return rows;
	}

}
