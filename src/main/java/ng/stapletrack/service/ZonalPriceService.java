package ng.stapletrack.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.entity.ZonalPrice;
import ng.stapletrack.entity.Zone;
import ng.stapletrack.repository.ZonalPriceRepository;

@Service
@Transactional(readOnly = true)
public class ZonalPriceService {

	private final ZonalPriceRepository repository;

	public ZonalPriceService(ZonalPriceRepository repository) {
		this.repository = repository;
	}

	/** Inserts the price, or overwrites it if this item/month/zone was imported before. */
	@Transactional
	public ZonalPrice upsert(String item, YearMonth monthYear, Zone zone, BigDecimal price) {
		BigDecimal normalized = Prices.toKobo(price);
		ZonalPrice record = repository.findByItemAndMonthYearAndZone(item, monthYear, zone)
				.orElseGet(() -> new ZonalPrice(item, monthYear, zone, normalized));
		record.setPrice(normalized);
		return repository.save(record);
	}

	/** All six zones for one item and month, cheapest first. */
	public List<ZonalPrice> rankedForMonth(String item, YearMonth monthYear) {
		return repository.findByItemAndMonthYearOrderByPriceAsc(item, monthYear);
	}

	public List<ZonalPrice> history(String item, Zone zone) {
		return repository.findByItemAndZoneOrderByMonthYearAsc(item, zone);
	}

	/** Every zone's history for an item; null bounds mean open-ended. */
	public List<ZonalPrice> historyAllZones(String item, YearMonth from, YearMonth to) {
		return repository.findByItemAndMonthYearBetweenOrderByMonthYearAscZoneAsc(item,
				Prices.fromOrEarliest(from), Prices.toOrLatest(to));
	}

}
