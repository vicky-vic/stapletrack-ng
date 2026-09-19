package ng.stapletrack.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How much more the most expensive place paid than the cheapest, phrased the same way on every page:
 * under 50% it reads as a percentage ("12% more"), from 50% up as a multiple ("2.1×").
 *
 * @param percent whole-number percentage difference, rounded half-up
 * @param ratio high ÷ low to one decimal place, rounded half-up
 * @param multiple true when the difference is 50% or more, so the multiple is the headline
 */
public record Spread(int percent, BigDecimal ratio, boolean multiple) {

	/** Differences at or above this percentage are shown as a multiple. */
	static final BigDecimal MULTIPLE_THRESHOLD_PERCENT = BigDecimal.valueOf(50);

	/**
	 * @throws IllegalArgumentException if {@code low} is not positive
	 */
	public static Spread between(BigDecimal low, BigDecimal high) {
		if (low.signum() <= 0) {
			throw new IllegalArgumentException("low price must be positive");
		}
		BigDecimal exactPercent = high.subtract(low).multiply(BigDecimal.valueOf(100)).divide(low, 10, RoundingMode.HALF_UP);
		int percent = exactPercent.setScale(0, RoundingMode.HALF_UP).intValueExact();
		BigDecimal ratio = high.divide(low, 1, RoundingMode.HALF_UP);
		return new Spread(percent, ratio, exactPercent.compareTo(MULTIPLE_THRESHOLD_PERCENT) >= 0);
	}

	/** Card headline: "12% more" or "2.1×". */
	public String label() {
		return multiple ? ratio.toPlainString() + "×" : percent + "% more";
	}

	/**
	 * Without "more", for "a spread of …": "12%" or "2.1×". In a sentence a multiple reads
	 * "cost 2.1× as much in A as in B", a percentage "cost 12% more in A than in B".
	 */
	public String shortLabel() {
		return multiple ? ratio.toPlainString() + "×" : percent + "%";
	}

}
