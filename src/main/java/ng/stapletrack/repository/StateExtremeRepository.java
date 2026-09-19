package ng.stapletrack.repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ng.stapletrack.entity.ExtremeKind;
import ng.stapletrack.entity.StateExtreme;

public interface StateExtremeRepository extends JpaRepository<StateExtreme, Long> {

	Optional<StateExtreme> findByItemAndMonthYearAndKind(String item, YearMonth monthYear, ExtremeKind kind);

	List<StateExtreme> findByItemAndMonthYearOrderByKindAsc(String item, YearMonth monthYear);

	List<StateExtreme> findByItemAndMonthYearBetweenOrderByMonthYearAscKindAsc(String item, YearMonth from, YearMonth to);

	@Query("select distinct e.item from StateExtreme e order by e.item")
	List<String> findDistinctItems();

	/** Newest first, for the month dropdown. */
	@Query("select distinct e.monthYear from StateExtreme e order by e.monthYear desc")
	List<YearMonth> findDistinctMonthsNewestFirst();

}
