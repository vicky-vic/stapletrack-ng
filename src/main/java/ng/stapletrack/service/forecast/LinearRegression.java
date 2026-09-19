package ng.stapletrack.service.forecast;

import java.util.List;

/**
 * Ordinary least squares for one predictor, in plain Java.
 *
 * <pre>
 * slope          = Σ((x − x̄)(y − ȳ)) / Σ((x − x̄)²)
 * intercept      = ȳ − slope · x̄
 * residualStdDev = √(Σ(y − ŷ)² / (n − 2))
 * prediction SE  = residualStdDev · √(1 + 1/n + (x − x̄)² / Σ((x − x̄)²))
 * </pre>
 */
public final class LinearRegression {

	/** Two parameters are estimated, so at least three points are needed for a residual spread. */
	public static final int MIN_POINTS = 3;

	private LinearRegression() {
	}

	/**
	 * A fitted line plus what {@link #predict} needs for prediction intervals.
	 *
	 * @param sumSquaredXDeviations Σ((x − x̄)²)
	 */
	public record Fit(double slope, double intercept, double residualStdDev, double meanX,
			double sumSquaredXDeviations, int n) {

		public double valueAt(double x) {
			return slope * x + intercept;
		}

	}

	/** A point estimate with its prediction interval. */
	public record Prediction(double pointEstimate, double lower, double upper) {
	}

	/**
	 * @throws IllegalArgumentException if the lists differ in size, have fewer than {@link #MIN_POINTS}
	 *         points, or all x values are equal
	 */
	public static Fit fit(List<Double> xs, List<Double> ys) {
		if (xs.size() != ys.size()) {
			throw new IllegalArgumentException("xs and ys must be the same size");
		}
		int n = xs.size();
		if (n < MIN_POINTS) {
			throw new IllegalArgumentException("need at least " + MIN_POINTS + " points, got " + n);
		}

		double meanX = 0;
		double meanY = 0;
		for (int i = 0; i < n; i++) {
			meanX += xs.get(i);
			meanY += ys.get(i);
		}
		meanX /= n;
		meanY /= n;

		double sxy = 0;
		double sxx = 0;
		for (int i = 0; i < n; i++) {
			double dx = xs.get(i) - meanX;
			sxy += dx * (ys.get(i) - meanY);
			sxx += dx * dx;
		}
		if (sxx == 0) {
			throw new IllegalArgumentException("all x values are equal; slope is undefined");
		}
		double slope = sxy / sxx;
		double intercept = meanY - slope * meanX;

		double sse = 0;
		for (int i = 0; i < n; i++) {
			double residual = ys.get(i) - (slope * xs.get(i) + intercept);
			sse += residual * residual;
		}
		double residualStdDev = Math.sqrt(sse / (n - 2));

		return new Fit(slope, intercept, residualStdDev, meanX, sxx, n);
	}

	/**
	 * Point estimate at {@code x} with a prediction interval of ± {@code zScore} standard errors
	 * (1.96 for 95%). The interval widens the further x is from the mean of the fitted xs.
	 */
	public static Prediction predict(Fit fit, double x, double zScore) {
		double point = fit.valueAt(x);
		double dx = x - fit.meanX();
		double standardError = fit.residualStdDev()
				* Math.sqrt(1 + 1.0 / fit.n() + dx * dx / fit.sumSquaredXDeviations());
		double margin = zScore * standardError;
		return new Prediction(point, point - margin, point + margin);
	}

}
