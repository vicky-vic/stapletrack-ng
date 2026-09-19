package ng.stapletrack.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.repository.NationalPriceRepository;
import ng.stapletrack.service.forecast.ForecastChart;
import ng.stapletrack.service.forecast.ForecastResult;
import ng.stapletrack.service.forecast.ForecastResult.ForecastPoint;
import ng.stapletrack.service.forecast.ForecastResult.PricePoint;
import ng.stapletrack.service.forecast.ForecastService;

/** Read-side queries that shape stored prices into chart-ready series. */
@Service
@Transactional(readOnly = true)
public class PriceQueryService {

	/**
	 * Staples charted on the dashboard, matched against NBS labels ignoring case and punctuation,
	 * so "Beans Brown Sold Loose" finds the NBS label "Beans brown,sold loose".
	 */
	static final List<String> DASHBOARD_STAPLES = List.of("Rice Local Sold Loose", "Beans Brown Sold Loose", "Yam Tuber");

	/** How far the dashboard previews project. */
	public static final int DASHBOARD_MONTHS_AHEAD = 3;

	private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

	private final NationalPriceRepository repository;
	private final ForecastService forecastService;

	public PriceQueryService(NationalPriceRepository repository, ForecastService forecastService) {
		this.repository = repository;
		this.forecastService = forecastService;
	}

	/**
	 * One series per dashboard staple, all on the same continuous month axis (first to last stored month)
	 * followed by the forecast months. Months with no data are null so the chart shows a gap.
	 */
	public List<PriceSeries> dashboardSeries() {
		List<YearMonth> stored = repository.findDistinctMonths();
		if (stored.isEmpty()) {
			return List.of();
		}
		List<YearMonth> axis = new ArrayList<>();
		for (YearMonth m = stored.getFirst(); !m.isAfter(stored.getLast()); m = m.plusMonths(1)) {
			axis.add(m);
		}

		List<String> items = repository.findDistinctItems();
		List<PriceSeries> series = new ArrayList<>();
		for (String wanted : DASHBOARD_STAPLES) {
			findItem(items, wanted).ifPresent(item -> series.add(seriesFor(item, axis)));
		}
		return series;
	}

	/** Full history as the solid line; the recent-trend forecast as the dashed tail. */
	private PriceSeries seriesFor(String item, List<YearMonth> axis) {
		List<PricePoint> history = repository.findByItemOrderByMonthYearAsc(item).stream()
				.map(p -> new PricePoint(p.getMonthYear(), p.getPrice())).toList();
		Optional<ForecastResult> forecast = forecastService.forecastRecent(item, DASHBOARD_MONTHS_AHEAD);
		List<ForecastChart.Series> projections = forecast.map(f -> List.of(new ForecastChart.Series(
				ForecastChart.Model.RECENT, "Forecast", f.forecast()))).orElse(List.of());
		ForecastChart chart = ForecastChart.of(item, history, projections, axis);

		PricePoint latest = history.getLast();
		return new PriceSeries(item, LABEL.format(latest.month()), latest.price(), history.size(), chart,
				forecast.map(f -> PriceSeries.horizonOf(f, history.size())).orElse(null));
	}

	private static Optional<String> findItem(List<String> items, String wanted) {
		String key = matchKey(wanted);
		return items.stream().filter(item -> matchKey(item).equals(key)).findFirst();
	}

	private static String matchKey(String label) {
		return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/**
	 * One dashboard card: latest actual price, the chart arrays, and the furthest recent-trend forecast
	 * month (null when there were too few points to fit a trend).
	 */
	public record PriceSeries(String item, String latestMonth, BigDecimal latestPrice, int monthsWithData,
			ForecastChart chart, Horizon horizon) {

		static Horizon horizonOf(ForecastResult forecast, int historySize) {
			ForecastPoint point = forecast.lastForecast();
			String basis = forecast.meta().n() < historySize
					? "the last " + ForecastService.RECENT_WINDOW_MONTHS + " months"
					: "all " + forecast.meta().n() + " months";
			return new Horizon(LABEL.format(point.month()), point.pointEstimate(), point.lower(), point.upper(), basis);
		}

	}

	/**
	 * The last projected month, pre-formatted for the dashboard subtitle.
	 *
	 * @param basis what the trend was fitted on, e.g. "the last 12 months"
	 */
	public record Horizon(String month, BigDecimal pointEstimate, BigDecimal lower, BigDecimal upper, String basis) {
	}

}
