package ng.stapletrack.service.forecast;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ng.stapletrack.service.forecast.ForecastResult.ForecastPoint;
import ng.stapletrack.service.forecast.ForecastResult.PricePoint;

/**
 * Chart.js-ready arrays, all aligned to {@code labels}. Null means "no point here".
 * Each projection's forecast and band series start at the last actual price so its dashed line
 * joins the solid one.
 */
public record ForecastChart(String item, List<String> labels, List<BigDecimal> actual, List<Projection> projections) {

	private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

	/** Which forecast a projection is, so the chart can colour it. */
	public enum Model {
		FULL, RECENT
	}

	/** One forecast laid out on the chart: point estimates plus the 95% band. */
	public record Projection(Model model, String label, List<BigDecimal> forecast, List<BigDecimal> upper,
			List<BigDecimal> lower) {
	}

	/** A forecast to draw, before it is aligned to the axis. */
	public record Series(Model model, String label, List<ForecastPoint> points) {
	}

	/**
	 * Lays history and forecasts out on {@code axis} (a continuous run of months), appending any
	 * forecast months that fall after its end.
	 */
	public static ForecastChart of(String item, List<PricePoint> history, List<Series> series, List<YearMonth> axis) {
		List<YearMonth> months = new ArrayList<>(axis);
		for (Series s : series) {
			for (ForecastPoint point : s.points()) {
				if (months.isEmpty() || point.month().isAfter(months.getLast())) {
					months.add(point.month());
				}
			}
		}
		Map<YearMonth, Integer> index = new HashMap<>();
		for (int i = 0; i < months.size(); i++) {
			index.put(months.get(i), i);
		}

		BigDecimal[] actual = new BigDecimal[months.size()];
		for (PricePoint point : history) {
			Integer i = index.get(point.month());
			if (i != null) {
				actual[i] = point.price();
			}
		}

		List<Projection> projections = new ArrayList<>();
		for (Series s : series) {
			if (s.points().isEmpty()) {
				continue;
			}
			BigDecimal[] projected = new BigDecimal[months.size()];
			BigDecimal[] upper = new BigDecimal[months.size()];
			BigDecimal[] lower = new BigDecimal[months.size()];
			if (!history.isEmpty()) {
				Integer join = index.get(history.getLast().month());
				if (join != null) {
					projected[join] = upper[join] = lower[join] = history.getLast().price();
				}
			}
			for (ForecastPoint point : s.points()) {
				int i = index.get(point.month());
				projected[i] = point.pointEstimate();
				upper[i] = point.upper();
				lower[i] = point.lower();
			}
			projections.add(new Projection(s.model(), s.label(), Arrays.asList(projected), Arrays.asList(upper),
					Arrays.asList(lower)));
		}
		return new ForecastChart(item, months.stream().map(LABEL::format).toList(), Arrays.asList(actual), projections);
	}

	/** Continuous run of months covering {@code history}. */
	public static List<YearMonth> axisFor(List<PricePoint> history) {
		List<YearMonth> axis = new ArrayList<>();
		if (history.isEmpty()) {
			return axis;
		}
		for (YearMonth m = history.getFirst().month(); !m.isAfter(history.getLast().month()); m = m.plusMonths(1)) {
			axis.add(m);
		}
		return axis;
	}

}
