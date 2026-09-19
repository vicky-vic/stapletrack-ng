package ng.stapletrack.repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ng.stapletrack.entity.NationalPrice;

public interface NationalPriceRepository extends JpaRepository<NationalPrice, Long> {

	Optional<NationalPrice> findByItemAndMonthYear(String item, YearMonth monthYear);

	List<NationalPrice> findByItemOrderByMonthYearAsc(String item);

	List<NationalPrice> findByItemAndMonthYearBetweenOrderByMonthYearAsc(String item, YearMonth from, YearMonth to);

	List<NationalPrice> findByMonthYearOrderByItemAsc(YearMonth monthYear);

	@Query("select distinct n.item from NationalPrice n order by n.item")
	List<String> findDistinctItems();

	@Query("select distinct n.monthYear from NationalPrice n order by n.monthYear")
	List<YearMonth> findDistinctMonths();

}
