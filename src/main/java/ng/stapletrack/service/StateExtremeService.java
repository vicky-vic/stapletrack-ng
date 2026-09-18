package ng.stapletrack.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.entity.ExtremeKind;
import ng.stapletrack.entity.StateExtreme;
import ng.stapletrack.repository.StateExtremeRepository;

@Service
@Transactional(readOnly = true)
public class StateExtremeService {

	private final StateExtremeRepository repository;

	public StateExtremeService(StateExtremeRepository repository) {
		this.repository = repository;
	}

	/** Inserts the extreme, or overwrites state and price if this item/month/kind was imported before. */
	@Transactional
	public StateExtreme upsert(String item, YearMonth monthYear, ExtremeKind kind, String state, BigDecimal price) {
		BigDecimal normalized = Prices.toKobo(price);
		StateExtreme record = repository.findByItemAndMonthYearAndKind(item, monthYear, kind)
				.orElseGet(() -> new StateExtreme(item, monthYear, kind, state, normalized));
		record.setState(state);
		record.setPrice(normalized);
		return repository.save(record);
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
