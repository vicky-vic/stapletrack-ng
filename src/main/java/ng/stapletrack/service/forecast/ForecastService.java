package ng.stapletrack.service.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.entity.NationalPrice;
import ng.stapletrack.repository.NationalPriceRepository;
import ng.stapletrack.service.forecast.ForecastResult.ForecastMeta;
import ng.stapletrack.service.forecast.ForecastResult.ForecastPoint;
import ng.stapletrack.service.forecast.ForecastResult.PricePoint;
import ng.stapletrack.service.forecast.LinearRegression.Fit;
import ng.stapletrack.service.forecast.LinearRegression.Prediction;

/** Projects national average prices forward with a straight-line (OLS) trend and a 95% prediction band. */
@Service
@Transactional(readOnly = true)
public class ForecastService {

	/** z for a two-sided 95% interval. */
	static final double Z_95 = 1.96;
	static final int MAX_MONTHS_AHEAD = 12;
	/** Calendar months the recent-trend forecast fits on. */
	public static final int RECENT_WINDOW_MONTHS = 12;

	private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

	private final NationalPriceRepository repository;

	public ForecastService(NationalPriceRepository repository) {
		this.repository = repository;
	}

	/**
	 * Full-history forecast: fits price against month over every stored price and projects
	 * {@code monthsAhead} months past the latest data point. Months are encoded as months since the
	 * earliest data point, so gaps (e.g. a missing release) keep their real width.
	 * Empty when the item has fewer than {@link LinearRegression#MIN_POINTS} prices.
	 */
	public Optional<ForecastResult> forecast(String item, int monthsAhead) {
		checkMonthsAhead(monthsAhead);
		List<NationalPrice> rows = repository.findByItemOrderByMonthYearAsc(item);
		return fitAndProject(item, rows, monthsAhead, "Full history (" + rows.size() + " months)");
	}

	/**
	 * Recent-trend forecast: like {@link #forecast} but fits only prices from the last
	 * {@value #RECENT_WINDOW_MONTHS} calendar months up to the latest data point (all data if the
	 * history is shorter). Follows the current slope more closely; more sensitive to short-term noise.
	 * The result's history holds only the fitted points.
	 */
	public Optional<ForecastResult> forecastRecent(String item, int monthsAhead) {
		checkMonthsAhead(monthsAhead);
		List<NationalPrice> rows = repository.findByItemOrderByMonthYearAsc(item);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		YearMonth windowStart = rows.getLast().getMonthYear().minusMonths(RECENT_WINDOW_MONTHS - 1);
		List<NationalPrice> window = rows.stream().filter(r -> !r.getMonthYear().isBefore(windowStart)).toList();
		String label = window.size() < rows.size()
				? "Recent trend (last " + RECENT_WINDOW_MONTHS + " months)"
				: "Recent trend (all " + rows.size() + " months)";
		return fitAndProject(item, window, monthsAhead, label);
	}

	private static void checkMonthsAhead(int monthsAhead) {
		if (monthsAhead < 1 || monthsAhead > MAX_MONTHS_AHEAD) {
			throw new IllegalArgumentException("monthsAhead must be 1–" + MAX_MONTHS_AHEAD);
		}
	}

	/** Fits a line to {@code rows} (oldest first) and projects past the last one. */
	private Optional<ForecastResult> fitAndProject(String item, List<NationalPrice> rows, int monthsAhead,
			String windowLabel) {
		if (rows.size() < LinearRegression.MIN_POINTS) {
			return Optional.empty();
		}

		YearMonth origin = rows.getFirst().getMonthYear();
		List<Double> xs = new ArrayList<>();
		List<Double> ys = new ArrayList<>();
		List<PricePoint> history = new ArrayList<>();
		for (NationalPrice row : rows) {
			xs.add((double) ChronoUnit.MONTHS.between(origin, row.getMonthYear()));
			ys.add(row.getPrice().doubleValue());
			history.add(new PricePoint(row.getMonthYear(), row.getPrice()));
		}

		Fit fit = LinearRegression.fit(xs, ys);
		YearMonth latest = rows.getLast().getMonthYear();
		double latestX = xs.getLast();

		List<ForecastPoint> forecast = new ArrayList<>();
		for (int k = 1; k <= monthsAhead; k++) {
			Prediction p = LinearRegression.predict(fit, latestX + k, Z_95);
			forecast.add(new ForecastPoint(latest.plusMonths(k), naira(p.pointEstimate()),
					naira(Math.max(0, p.lower())), naira(p.upper())));
		}

		ForecastMeta meta = new ForecastMeta(fit.n(), rSquared(fit, xs, ys),
				LABEL.format(origin) + " – " + LABEL.format(latest), windowLabel);
		return Optional.of(new ForecastResult(item, history, forecast, meta));
	}

	/** Items that have national prices, alphabetical — the forecast page's dropdown. */
	public List<String> items() {
		return repository.findDistinctItems();
	}

	/** R² = 1 − Σ(y − ŷ)² / Σ(y − ȳ)²; a perfectly flat series counts as fully explained. */
	static double rSquared(Fit fit, List<Double> xs, List<Double> ys) {
		double meanY = ys.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		double sse = 0;
		double sst = 0;
		for (int i = 0; i < ys.size(); i++) {
			double residual = ys.get(i) - fit.valueAt(xs.get(i));
			sse += residual * residual;
			double deviation = ys.get(i) - meanY;
			sst += deviation * deviation;
		}
		return sst == 0 ? 1.0 : 1 - sse / sst;
	}

	private static BigDecimal naira(double value) {
		return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
	}

}
