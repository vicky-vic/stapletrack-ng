package ng.stapletrack.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.entity.Zone;
import ng.stapletrack.repository.ZonalPriceRepository;

/** Zone-by-zone price comparison for the regional gaps page. */
@Service
@Transactional(readOnly = true)
public class ZonesService {

	/** --st-green-700: the cheapest zone's fill. */
	static final String CHEAPEST_COLOR = "#1B4332";
	/** --st-amber-500: the most expensive zone's fill. */
	static final String PRICIEST_COLOR = "#E9A83A";

	private final ZonalPriceRepository repository;

	public ZonesService(ZonalPriceRepository repository) {
		this.repository = repository;
	}

	/**
	 * Every zone's price for an item and month, cheapest first, with the cheapest, most expensive and
	 * spread derived from it, plus a colour for each zone that has data.
	 * Empty when fewer than two zones have a price (nothing to compare).
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
				Spread.between(cheapest.price(), priciest.price()), computeHexColors(zones)));
	}

	/**
	 * Map fill per zone, by price rank: the cheapest gets {@link #CHEAPEST_COLOR}, the most expensive
	 * {@link #PRICIEST_COLOR}, and the zones between are spaced evenly between them in HSL (hue, saturation
	 * and lightness each interpolated), which passes through clean greens and yellows rather than the muddy
	 * browns a straight RGB blend gives. Only zones in {@code sortedAsc} appear in the result.
	 *
	 * @param sortedAsc zones cheapest first
	 */
	public static Map<Zone, String> computeHexColors(List<ZonePrice> sortedAsc) {
		Map<Zone, String> colors = new EnumMap<>(Zone.class);
		if (sortedAsc.isEmpty()) {
			return colors;
		}
		if (sortedAsc.size() == 1) {
			colors.put(sortedAsc.getFirst().zone(), CHEAPEST_COLOR);
			return colors;
		}
		double[] from = rgbToHsl(CHEAPEST_COLOR);
		double[] to = rgbToHsl(PRICIEST_COLOR);
		int last = sortedAsc.size() - 1;
		for (int rank = 0; rank <= last; rank++) {
			String color;
			if (rank == 0) {
				color = CHEAPEST_COLOR;
			}
			else if (rank == last) {
				color = PRICIEST_COLOR;
			}
			else {
				double t = (double) rank / last;
				color = hslToHex(lerp(from[0], to[0], t), lerp(from[1], to[1], t), lerp(from[2], to[2], t));
			}
			colors.put(sortedAsc.get(rank).zone(), color);
		}
		return colors;
	}

	/** Hue of a "#RRGGBB" colour in degrees, 0–360. */
	static double hue(String hex) {
		return rgbToHsl(hex)[0];
	}

	/** Items that have zonal prices, alphabetical. */
	public List<String> items() {
		return repository.findDistinctItems();
	}

	/** Months that have zonal prices, newest first. */
	public List<YearMonth> months() {
		return repository.findDistinctMonthsNewestFirst();
	}

	// ---- colour helpers ----

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/** "#RRGGBB" → {hue 0–360, saturation 0–1, lightness 0–1}. */
	private static double[] rgbToHsl(String hex) {
		double[] rgb = rgb(hex);
		double max = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
		double min = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
		double l = (max + min) / 2;
		double d = max - min;
		if (d == 0) {
			return new double[] { 0, 0, l };
		}
		double s = d / (1 - Math.abs(2 * l - 1));
		double h;
		if (max == rgb[0]) {
			h = 60 * (((rgb[1] - rgb[2]) / d) % 6);
		}
		else if (max == rgb[1]) {
			h = 60 * ((rgb[2] - rgb[0]) / d + 2);
		}
		else {
			h = 60 * ((rgb[0] - rgb[1]) / d + 4);
		}
		return new double[] { h < 0 ? h + 360 : h, s, l };
	}

	private static String hslToHex(double h, double s, double l) {
		double c = (1 - Math.abs(2 * l - 1)) * s;
		double x = c * (1 - Math.abs((h / 60) % 2 - 1));
		double m = l - c / 2;
		double r;
		double g;
		double b;
		if (h < 60) {
			r = c; g = x; b = 0;
		}
		else if (h < 120) {
			r = x; g = c; b = 0;
		}
		else if (h < 180) {
			r = 0; g = c; b = x;
		}
		else if (h < 240) {
			r = 0; g = x; b = c;
		}
		else if (h < 300) {
			r = x; g = 0; b = c;
		}
		else {
			r = c; g = 0; b = x;
		}
		return String.format(Locale.ROOT, "#%02X%02X%02X",
				Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
	}

	private static double[] rgb(String hex) {
		int value = Integer.parseInt(hex.substring(1), 16);
		return new double[] { ((value >> 16) & 0xFF) / 255.0, ((value >> 8) & 0xFF) / 255.0, (value & 0xFF) / 255.0 };
	}

	/** One zone's price, with its display name. */
	public record ZonePrice(Zone zone, String name, BigDecimal price) {
	}

	/**
	 * @param zones cheapest first
	 * @param hexColors map and bar fill per zone with data (zones without data are absent)
	 */
	public record ZonalBreakdown(String item, YearMonth month, List<ZonePrice> zones, ZonePrice cheapest,
			ZonePrice priciest, Spread spread, Map<Zone, String> hexColors) {
	}

}
