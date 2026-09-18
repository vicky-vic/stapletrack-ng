package ng.stapletrack.parser;

import java.time.YearMonth;
import java.util.List;

import ng.stapletrack.entity.NationalPrice;
import ng.stapletrack.entity.StateExtreme;
import ng.stapletrack.entity.ZonalPrice;

/**
 * Everything read from one NBS Selected Food Prices Watch workbook, not yet persisted.
 *
 * @param currentMonth the release month (newest "Average of" column); zonal and extreme rows are keyed to it
 * @param warnings human-readable notes about rows or cells that were skipped
 */
public record ParsedFile(
		YearMonth currentMonth,
		List<NationalPrice> nationalPrices,
		List<StateExtreme> stateExtremes,
		List<ZonalPrice> zonalPrices,
		List<String> warnings) {
}
