package ng.stapletrack.controller;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ng.stapletrack.entity.Zone;
import ng.stapletrack.service.NigeriaMapRenderer;
import ng.stapletrack.service.ZonesService;
import ng.stapletrack.service.ZonesService.ZonalBreakdown;
import ng.stapletrack.service.ZonesService.ZonePrice;

@Controller
public class ZonesController {

	static final String DEFAULT_ITEM = "Rice local sold loose";

	private final ZonesService zonesService;
	private final NigeriaMapRenderer mapRenderer;
	private final ObjectMapper objectMapper;

	public ZonesController(ZonesService zonesService, NigeriaMapRenderer mapRenderer, ObjectMapper objectMapper) {
		this.zonesService = zonesService;
		this.mapRenderer = mapRenderer;
		this.objectMapper = objectMapper;
	}

	/**
	 * GET /zones?item=Tomato&month=2024-02. Missing or unknown parameters fall back to
	 * the default item and the newest month rather than an error page.
	 */
	@GetMapping("/zones")
	public String zones(@RequestParam(required = false) String item, @RequestParam(required = false) String month,
			Model model) throws JsonProcessingException {
		model.addAttribute("currentPage", "zones");

		List<String> items = zonesService.items();
		List<YearMonth> months = zonesService.months();
		model.addAttribute("hasData", !items.isEmpty());
		if (items.isEmpty()) {
			return "zones";
		}

		String selectedItem = item != null && items.contains(item) ? item
				: (items.contains(DEFAULT_ITEM) ? DEFAULT_ITEM : items.getFirst());
		YearMonth selectedMonth = parseMonth(month);
		if (selectedMonth == null || !months.contains(selectedMonth)) {
			selectedMonth = months.getFirst();
		}

		ZonalBreakdown breakdown = zonesService.findZonalBreakdown(selectedItem, selectedMonth).orElse(null);
		model.addAttribute("items", items);
		model.addAttribute("months", months);
		model.addAttribute("selectedItem", selectedItem);
		model.addAttribute("selectedMonth", selectedMonth);
		model.addAttribute("breakdown", breakdown);
		if (breakdown != null) {
			// Fill per zone (zones without data are absent); the map and the bars share these colours
			model.addAttribute("hexColors", breakdown.hexColors());
			// Trusted markup: our own classpath SVG with server-generated styles and escaped titles
			model.addAttribute("nigeriaMapSvg", mapRenderer.render(breakdown.hexColors(), pricesByZone(breakdown)));
			// Serialized with Jackson; the template embeds it as an escaped JS string and JSON.parse()s it
			model.addAttribute("chartJson", objectMapper.writeValueAsString(chart(breakdown)));
		}
		return "zones";
	}

	/** Bar chart payload: zones cheapest first, with prices and bar colours aligned to labels. */
	record ZoneChart(List<String> labels, List<BigDecimal> prices, List<String> colors) {
	}

	private static ZoneChart chart(ZonalBreakdown breakdown) {
		return new ZoneChart(breakdown.zones().stream().map(ZonePrice::name).toList(),
				breakdown.zones().stream().map(ZonePrice::price).toList(),
				breakdown.zones().stream().map(z -> breakdown.hexColors().get(z.zone())).toList());
	}

	private static Map<Zone, BigDecimal> pricesByZone(ZonalBreakdown breakdown) {
		Map<Zone, BigDecimal> prices = new EnumMap<>(Zone.class);
		breakdown.zones().forEach(z -> prices.put(z.zone(), z.price()));
		return prices;
	}

	private static YearMonth parseMonth(String month) {
		if (month == null || month.isBlank()) {
			return null;
		}
		try {
			return YearMonth.parse(month.trim());
		}
		catch (DateTimeParseException e) {
			return null;
		}
	}

}
