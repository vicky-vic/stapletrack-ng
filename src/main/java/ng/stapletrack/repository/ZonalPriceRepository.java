package ng.stapletrack.repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ng.stapletrack.entity.ZonalPrice;
import ng.stapletrack.entity.Zone;

public interface ZonalPriceRepository extends JpaRepository<ZonalPrice, Long> {

	Optional<ZonalPrice> findByItemAndMonthYearAndZone(String item, YearMonth monthYear, Zone zone);

	/** One month's zones for an item, cheapest first. */
	List<ZonalPrice> findByItemAndMonthYearOrderByPriceAsc(String item, YearMonth monthYear);

	List<ZonalPrice> findByItemAndZoneOrderByMonthYearAsc(String item, Zone zone);

	List<ZonalPrice> findByItemAndMonthYearBetweenOrderByMonthYearAscZoneAsc(String item, YearMonth from, YearMonth to);

}
