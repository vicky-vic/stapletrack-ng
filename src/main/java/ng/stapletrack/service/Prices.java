package ng.stapletrack.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/** Shared helpers so every table stores naira to the kobo and treats null date bounds as open-ended. */
final class Prices {

	private static final YearMonth EARLIEST = YearMonth.of(1900, 1);
	private static final YearMonth LATEST = YearMonth.of(9999, 12);

	private Prices() {
	}

	static BigDecimal toKobo(BigDecimal price) {
		return price.setScale(2, RoundingMode.HALF_UP);
	}

	static YearMonth fromOrEarliest(YearMonth from) {
		return from != null ? from : EARLIEST;
	}

	static YearMonth toOrLatest(YearMonth to) {
		return to != null ? to : LATEST;
	}

}
