package ng.stapletrack.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.entity.Zone;
import ng.stapletrack.repository.ZonalPriceRepository;

/** Zone-by-zone price comparison for the regional gaps page. */
@Service
@Transactional(readOnly = true)
public class ZonesService {

	private final ZonalPriceRepository repository;

	public ZonesService(ZonalPriceRepository repository) {
		this.repository = repository;
	}

	/**
	 * Every zone's price for an item and month, cheapest first, with the cheapest, most expensive and
	 * spread derived from it. Empty when fewer than two zones have a price (nothing to compare).
	 */
	public Optional<ZonalBreakdown> findZonalBreakdown(String item, YearMonth month) {
		List<ZonePrice> zones = repository.findByItemAndMonthYearOrderByPriceAsc(item, month).stream()
				.map(z -> new ZonePrice(z.getZone(), Zones.displayName(z.getZone()), z.getPrice()))
				.toList();
		if (zones.size() < 2) {
			return Optional.empty();
		}
		ZonePrice cheapest = zones.getFirst();
		ZonePrice priciest = zones.getLast();
		return Optional.of(new ZonalBreakdown(item, month, zones, cheapest, priciest,
				Spread.between(cheapest.price(), priciest.price())));
	}

	/** Items that have zonal prices, alphabetical. */
	public List<String> items() {
		return repository.findDistinctItems();
	}

	/** Months that have zonal prices, newest first. */
	public List<YearMonth> months() {
		return repository.findDistinctMonthsNewestFirst();
	}

	/** One zone's price, with its display name. */
	public record ZonePrice(Zone zone, String name, BigDecimal price) {
	}

	/**
	 * @param zones cheapest first
	 */
	public record ZonalBreakdown(String item, YearMonth month, List<ZonePrice> zones, ZonePrice cheapest,
			ZonePrice priciest, Spread spread) {
	}

}
