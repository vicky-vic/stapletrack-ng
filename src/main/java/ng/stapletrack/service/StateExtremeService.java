package ng.stapletrack.service;

import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.entity.StateExtreme;
import ng.stapletrack.repository.StateExtremeRepository;

@Service
@Transactional(readOnly = true)
public class StateExtremeService {

	private final StateExtremeRepository repository;

	public StateExtremeService(StateExtremeRepository repository) {
		this.repository = repository;
	}

	/** The HIGHEST and LOWEST rows for one item and month. */
	public List<StateExtreme> forMonth(String item, YearMonth monthYear) {
		return repository.findByItemAndMonthYearOrderByKindAsc(item, monthYear);
	}

	/** Highest/lowest history for an item; null bounds mean open-ended. */
	public List<StateExtreme> history(String item, YearMonth from, YearMonth to) {
		return repository.findByItemAndMonthYearBetweenOrderByMonthYearAscKindAsc(item,
				Prices.fromOrEarliest(from), Prices.toOrLatest(to));
	}

}
