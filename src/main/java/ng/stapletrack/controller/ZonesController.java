package ng.stapletrack.controller;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ng.stapletrack.service.ZonesService;
import ng.stapletrack.service.ZonesService.ZonalBreakdown;
import ng.stapletrack.service.ZonesService.ZonePrice;

@Controller
public class ZonesController {

	static final String DEFAULT_ITEM = "Rice local sold loose";

	private final ZonesService zonesService;
	private final ObjectMapper objectMapper;

	public ZonesController(ZonesService zonesService, ObjectMapper objectMapper) {
		this.zonesService = zonesService;
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
		// Serialized with Jackson; the template embeds it as an escaped JS string and JSON.parse()s it
		model.addAttribute("chartJson", breakdown == null ? "null" : objectMapper.writeValueAsString(chart(breakdown)));
		return "zones";
	}

	/** Bar chart payload: zones cheapest first, with prices aligned to labels. */
	record ZoneChart(List<String> labels, List<BigDecimal> prices) {
	}

	private static ZoneChart chart(ZonalBreakdown breakdown) {
		return new ZoneChart(breakdown.zones().stream().map(ZonePrice::name).toList(),
				breakdown.zones().stream().map(ZonePrice::price).toList());
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
