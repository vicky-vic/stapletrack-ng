package ng.stapletrack.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.entity.NationalPrice;
import ng.stapletrack.repository.NationalPriceRepository;

/** Read-side queries that shape stored prices into chart-ready series. */
@Service
@Transactional(readOnly = true)
public class PriceQueryService {

	/**
	 * Staples charted on the dashboard, matched against NBS labels ignoring case and punctuation,
	 * so "Beans Brown Sold Loose" finds the NBS label "Beans brown,sold loose".
	 */
	static final List<String> DASHBOARD_STAPLES = List.of("Rice Local Sold Loose", "Beans Brown Sold Loose", "Yam Tuber");

	private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

	private final NationalPriceRepository repository;

	public PriceQueryService(NationalPriceRepository repository) {
		this.repository = repository;
	}

	/**
	 * One series per dashboard staple, all on the same continuous month axis (first to last stored month).
	 * Months with no data are null so the chart shows a gap rather than drawing a line across it.
	 */
	public List<PriceSeries> dashboardSeries() {
		List<YearMonth> stored = repository.findDistinctMonths();
		if (stored.isEmpty()) {
			return List.of();
		}
		List<YearMonth> axis = new ArrayList<>();
		for (YearMonth m = stored.getFirst(); !m.isAfter(stored.getLast()); m = m.plusMonths(1)) {
			axis.add(m);
		}
		List<String> labels = axis.stream().map(LABEL::format).toList();

		List<String> items = repository.findDistinctItems();
		List<PriceSeries> series = new ArrayList<>();
		for (String wanted : DASHBOARD_STAPLES) {
			findItem(items, wanted).ifPresent(item -> series.add(seriesFor(item, axis, labels)));
		}
		return series;
	}

	private PriceSeries seriesFor(String item, List<YearMonth> axis, List<String> labels) {
		Map<YearMonth, BigDecimal> byMonth = new HashMap<>();
		for (NationalPrice price : repository.findByItemAndMonthYearBetweenOrderByMonthYearAsc(item, axis.getFirst(),
				axis.getLast())) {
			byMonth.put(price.getMonthYear(), price.getPrice());
		}
		List<BigDecimal> values = axis.stream().map(byMonth::get).toList();

		YearMonth latest = byMonth.keySet().stream().max(YearMonth::compareTo).orElseThrow();
		return new PriceSeries(item, labels, values, LABEL.format(latest), byMonth.get(latest), byMonth.size());
	}

	private static Optional<String> findItem(List<String> items, String wanted) {
		String key = matchKey(wanted);
		return items.stream().filter(item -> matchKey(item).equals(key)).findFirst();
	}

	private static String matchKey(String label) {
		return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/**
	 * Chart payload for one item. {@code values} lines up with {@code labels}; null means no data that month.
	 */
	public record PriceSeries(String item, List<String> labels, List<BigDecimal> values, String latestMonth,
			BigDecimal latestPrice, int monthsWithData) {
	}

}
