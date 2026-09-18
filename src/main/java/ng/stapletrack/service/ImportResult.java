package ng.stapletrack.service;

import java.time.YearMonth;
import java.util.List;

/**
 * Outcome of importing one NBS workbook. "Updated" means a row with the same unique key
 * already existed (e.g. the month was also in a previously uploaded release) and its price was overwritten.
 */
public record ImportResult(
		YearMonth releaseMonth,
		int nationalInserted,
		int nationalUpdated,
		int zonalInserted,
		int zonalUpdated,
		int extremesInserted,
		int extremesUpdated,
		List<String> warnings) {

	public int nationalTotal() {
		return nationalInserted + nationalUpdated;
	}

	public int zonalTotal() {
		return zonalInserted + zonalUpdated;
	}

	public int extremesTotal() {
		return extremesInserted + extremesUpdated;
	}

}
