package ng.stapletrack.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.entity.ExtremeKind;
import ng.stapletrack.entity.StateExtreme;
import ng.stapletrack.repository.StateExtremeRepository;

/** Cheapest/most-expensive state lookups for the extremes explorer. */
@Service
@Transactional(readOnly = true)
public class ExtremesService {

	private final StateExtremeRepository repository;

	public ExtremesService(StateExtremeRepository repository) {
		this.repository = repository;
	}

	/**
	 * The cheapest and priciest state for an item in a month, plus the same pair twelve months earlier
	 * when that month was imported too. Empty when either extreme is missing for the requested month.
	 */
	public Optional<Extremes> findExtremes(String item, YearMonth month) {
		return findPair(item, month).map(current -> new Extremes(item, month, current.cheapest(), current.priciest(),
				findPair(item, month.minusYears(1)).orElse(null)));
	}

	/** Items that have state extremes, alphabetical. */
	public List<String> items() {
		return repository.findDistinctItems();
	}

	/** Months that have state extremes, newest first. */
	public List<YearMonth> months() {
		return repository.findDistinctMonthsNewestFirst();
	}

	private Optional<StatePair> findPair(String item, YearMonth month) {
		Optional<StateExtreme> lowest = repository.findByItemAndMonthYearAndKind(item, month, ExtremeKind.LOWEST);
		Optional<StateExtreme> highest = repository.findByItemAndMonthYearAndKind(item, month, ExtremeKind.HIGHEST);
		if (lowest.isEmpty() || highest.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new StatePair(month, toStatePrice(lowest.get()), toStatePrice(highest.get())));
	}

	private static StatePrice toStatePrice(StateExtreme extreme) {
		return new StatePrice(StateNames.canonicalize(extreme.getState()), extreme.getPrice());
	}

	/** A state and what the item cost there. The state name is already canonicalized. */
	public record StatePrice(String state, BigDecimal price) {
	}

	/** Cheapest and priciest state for one month. */
	public record StatePair(YearMonth month, StatePrice cheapest, StatePrice priciest) {
	}

	/**
	 * Result for the explorer.
	 *
	 * @param yearEarlier the same pair twelve months earlier, or null when that month has no extremes
	 */
	public record Extremes(String item, YearMonth month, StatePrice cheapest, StatePrice priciest,
			StatePair yearEarlier) {

		/** How much more the priciest state paid than the cheapest: "12% more" or "2.1×" (see {@link Spread}). */
		public Spread spread() {
			return Spread.between(cheapest.price(), priciest.price());
		}

		public boolean hasYearEarlier() {
			return yearEarlier != null;
		}

	}

}
