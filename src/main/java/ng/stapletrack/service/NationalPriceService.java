package ng.stapletrack.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.entity.NationalPrice;
import ng.stapletrack.repository.NationalPriceRepository;

@Service
@Transactional(readOnly = true)
public class NationalPriceService {

	private final NationalPriceRepository repository;

	public NationalPriceService(NationalPriceRepository repository) {
		this.repository = repository;
	}

	/** Inserts the price, or overwrites it if this item/month was imported before. */
	@Transactional
	public NationalPrice upsert(String item, YearMonth monthYear, BigDecimal price) {
		BigDecimal normalized = Prices.toKobo(price);
		NationalPrice record = repository.findByItemAndMonthYear(item, monthYear)
				.orElseGet(() -> new NationalPrice(item, monthYear, normalized));
		record.setPrice(normalized);
		return repository.save(record);
	}

	/** Price history for an item; null bounds mean open-ended. */
	public List<NationalPrice> history(String item, YearMonth from, YearMonth to) {
		return repository.findByItemAndMonthYearBetweenOrderByMonthYearAsc(item,
				Prices.fromOrEarliest(from), Prices.toOrLatest(to));
	}

	public List<NationalPrice> forMonth(YearMonth monthYear) {
		return repository.findByMonthYearOrderByItemAsc(monthYear);
	}

	public List<String> items() {
		return repository.findDistinctItems();
	}

	public List<YearMonth> months() {
		return repository.findDistinctMonths();
	}

}
