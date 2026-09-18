package ng.stapletrack.service;

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
