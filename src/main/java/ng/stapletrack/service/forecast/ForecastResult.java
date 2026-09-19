package ng.stapletrack.service.forecast;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * A linear-trend forecast for one item: the history it was fitted on (the whole series, or just the
 * recent window), the projected months and fit quality.
 */
public record ForecastResult(String item, List<PricePoint> history, List<ForecastPoint> forecast, ForecastMeta meta) {

	/** One observed national average price. */
	public record PricePoint(YearMonth month, BigDecimal price) {
	}

	/** One projected month with its 95% prediction interval. Lower is floored at zero. */
	public record ForecastPoint(YearMonth month, BigDecimal pointEstimate, BigDecimal lower, BigDecimal upper) {
	}

	/**
	 * @param n points the line was fitted on
	 * @param rSquared share of price variation the straight line explains, 0–1
	 * @param sampleWindow e.g. "Nov 2022 – Oct 2024"
	 * @param windowLabel which model this is, e.g. "Full history (23 months)" or "Recent trend (last 12 months)"
	 */
	public record ForecastMeta(int n, double rSquared, String sampleWindow, String windowLabel) {

		/** Plain-English reading of R² for the page. */
		public String fitDescription() {
			if (rSquared > 0.85) {
				return "strong linear trend";
			}
			if (rSquared > 0.6) {
				return "moderate linear trend";
			}
			return "weak — take with caution";
		}

	}

	public ForecastPoint nextMonth() {
		return forecast.getFirst();
	}

	public ForecastPoint lastForecast() {
		return forecast.getLast();
	}

	public PricePoint latest() {
		return history.getLast();
	}

}
