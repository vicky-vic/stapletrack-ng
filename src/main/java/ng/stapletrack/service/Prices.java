package ng.stapletrack.service;

import java.time.YearMonth;

/** Shared helper so every history query treats null date bounds as open-ended. */
final class Prices {

	private static final YearMonth EARLIEST = YearMonth.of(1900, 1);
	private static final YearMonth LATEST = YearMonth.of(9999, 12);

	private Prices() {
	}

	static YearMonth fromOrEarliest(YearMonth from) {
		return from != null ? from : EARLIEST;
	}

	static YearMonth toOrLatest(YearMonth to) {
		return to != null ? to : LATEST;
	}

}
