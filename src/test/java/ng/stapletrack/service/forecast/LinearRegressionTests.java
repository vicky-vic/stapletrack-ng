package ng.stapletrack.service.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import ng.stapletrack.service.forecast.LinearRegression.Fit;
import ng.stapletrack.service.forecast.LinearRegression.Prediction;

class LinearRegressionTests {

	private static final double EPS = 1e-9;

	@Test
	void recoversExactLine() {
		// y = 2x + 1
		Fit fit = LinearRegression.fit(List.of(0.0, 1.0, 2.0, 3.0, 4.0), List.of(1.0, 3.0, 5.0, 7.0, 9.0));

		assertThat(fit.slope()).isCloseTo(2.0, within(EPS));
		assertThat(fit.intercept()).isCloseTo(1.0, within(EPS));
		assertThat(fit.residualStdDev()).isCloseTo(0.0, within(EPS));
		assertThat(fit.meanX()).isCloseTo(2.0, within(EPS));
		assertThat(fit.sumSquaredXDeviations()).isCloseTo(10.0, within(EPS));
		assertThat(fit.n()).isEqualTo(5);

		Prediction p = LinearRegression.predict(fit, 10, 1.96);
		assertThat(p.pointEstimate()).isCloseTo(21.0, within(EPS));
		assertThat(p.lower()).isCloseTo(21.0, within(EPS));
		assertThat(p.upper()).isCloseTo(21.0, within(EPS));
	}

	@Test
	void matchesHandComputedNoisyExample() {
		// x̄ = 3, ȳ = 4, Sxy = 6, Sxx = 10 → slope 0.6, intercept 2.2
		// residuals −0.8, 0.6, 1.0, −0.6, −0.2 → SSE 2.4 → s = √(2.4 / 3)
		Fit fit = LinearRegression.fit(List.of(1.0, 2.0, 3.0, 4.0, 5.0), List.of(2.0, 4.0, 5.0, 4.0, 5.0));

		assertThat(fit.slope()).isCloseTo(0.6, within(EPS));
		assertThat(fit.intercept()).isCloseTo(2.2, within(EPS));
		assertThat(fit.residualStdDev()).isCloseTo(Math.sqrt(0.8), within(EPS));

		// At x = 6: ŷ = 5.8, SE = s·√(1 + 1/5 + 9/10) = √0.8·√2.1
		Prediction p = LinearRegression.predict(fit, 6, 1.96);
		double se = Math.sqrt(0.8) * Math.sqrt(2.1);
		assertThat(p.pointEstimate()).isCloseTo(5.8, within(EPS));
		assertThat(p.lower()).isCloseTo(5.8 - 1.96 * se, within(EPS));
		assertThat(p.upper()).isCloseTo(5.8 + 1.96 * se, within(EPS));
	}

	@Test
	void bandWidensFurtherOut() {
		Fit fit = LinearRegression.fit(List.of(0.0, 1.0, 2.0, 3.0, 4.0, 5.0),
				List.of(100.0, 112.0, 118.0, 133.0, 139.0, 152.0));

		double previousWidth = 0;
		for (int k = 1; k <= 3; k++) {
			Prediction p = LinearRegression.predict(fit, 5 + k, 1.96);
			double width = p.upper() - p.lower();
			assertThat(width).isGreaterThan(previousWidth);
			assertThat(p.lower()).isLessThan(p.pointEstimate());
			assertThat(p.upper()).isGreaterThan(p.pointEstimate());
			previousWidth = width;
		}
	}

	@Test
	void bandIsNarrowestAtTheMeanOfX() {
		Fit fit = LinearRegression.fit(List.of(0.0, 1.0, 2.0, 3.0, 4.0), List.of(2.0, 4.0, 5.0, 4.0, 5.0));
		double atMean = width(LinearRegression.predict(fit, fit.meanX(), 1.96));
		assertThat(width(LinearRegression.predict(fit, fit.meanX() + 1, 1.96))).isGreaterThan(atMean);
		assertThat(width(LinearRegression.predict(fit, fit.meanX() - 1, 1.96))).isGreaterThan(atMean);
	}

	/**
	 * 12 flat months at 100 then 12 months rising 10/month (110 … 220) — the shape that made the
	 * full-window forecast start below the latest price.
	 */
	private static List<Double> flatThenRising() {
		List<Double> ys = new java.util.ArrayList<>();
		for (int i = 0; i < 12; i++) {
			ys.add(100.0);
		}
		for (int i = 1; i <= 12; i++) {
			ys.add(100.0 + 10 * i);
		}
		return ys;
	}

	private static List<Double> xs(int from, int count) {
		List<Double> xs = new java.util.ArrayList<>();
		for (int i = 0; i < count; i++) {
			xs.add((double) (from + i));
		}
		return xs;
	}

	@Test
	void twelveMonthWindowRecoversRecentSlope() {
		List<Double> ys = flatThenRising();
		// Last 12 points, re-based so the window starts at x = 0
		Fit recent = LinearRegression.fit(xs(0, 12), ys.subList(12, 24));

		assertThat(recent.slope()).isCloseTo(10.0, within(EPS));
		assertThat(recent.intercept()).isCloseTo(110.0, within(EPS));
		assertThat(recent.residualStdDev()).isCloseTo(0.0, within(EPS));
		// Next month continues the line from the latest price (220) rather than dropping below it
		assertThat(LinearRegression.predict(recent, 12, 1.96).pointEstimate()).isCloseTo(230.0, within(EPS));
	}

	@Test
	void fullWindowUnderProjectsWhereTwelveMonthWindowDoesNot() {
		List<Double> ys = flatThenRising();
		double latest = ys.getLast();

		Fit full = LinearRegression.fit(xs(0, 24), ys);
		Fit recent = LinearRegression.fit(xs(0, 12), ys.subList(12, 24));

		double fullNext = LinearRegression.predict(full, 24, 1.96).pointEstimate();
		double recentNext = LinearRegression.predict(recent, 12, 1.96).pointEstimate();

		assertThat(full.slope()).isLessThan(recent.slope());
		assertThat(fullNext).isLessThan(latest);
		assertThat(recentNext).isGreaterThan(latest);
	}

	@Test
	void windowOriginDoesNotChangePredictions() {
		// Fitting the same 12 points at x = 12..23 or re-based to 0..11 gives the same projection
		List<Double> window = flatThenRising().subList(12, 24);
		Prediction absolute = LinearRegression.predict(LinearRegression.fit(xs(12, 12), window), 24, 1.96);
		Prediction rebased = LinearRegression.predict(LinearRegression.fit(xs(0, 12), window), 12, 1.96);

		assertThat(absolute.pointEstimate()).isCloseTo(rebased.pointEstimate(), within(1e-6));
		assertThat(absolute.upper() - absolute.lower()).isCloseTo(rebased.upper() - rebased.lower(), within(1e-6));
	}

	@Test
	void rejectsTooFewPointsOrConstantX() {
		assertThatThrownBy(() -> LinearRegression.fit(List.of(1.0, 2.0), List.of(1.0, 2.0)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> LinearRegression.fit(List.of(3.0, 3.0, 3.0), List.of(1.0, 2.0, 3.0)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> LinearRegression.fit(List.of(1.0, 2.0, 3.0), List.of(1.0, 2.0)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static double width(Prediction p) {
		return p.upper() - p.lower();
	}

}
