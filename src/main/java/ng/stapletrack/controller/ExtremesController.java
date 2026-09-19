package ng.stapletrack.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ng.stapletrack.service.ExtremesService;

@Controller
public class ExtremesController {

	static final String DEFAULT_ITEM = "Rice local sold loose";

	private final ExtremesService extremesService;

	public ExtremesController(ExtremesService extremesService) {
		this.extremesService = extremesService;
	}

	/**
	 * GET /extremes?item=Yam tuber&month=2024-10. Missing or unknown parameters fall back to
	 * the default item and the newest month rather than an error page.
	 */
	@GetMapping("/extremes")
	public String extremes(@RequestParam(required = false) String item,
			@RequestParam(required = false) String month, Model model) {
		model.addAttribute("currentPage", "extremes");

		List<String> items = extremesService.items();
		List<YearMonth> months = extremesService.months();
		model.addAttribute("hasData", !items.isEmpty());
		if (items.isEmpty()) {
			return "extremes";
		}

		String selectedItem = item != null && items.contains(item) ? item
				: (items.contains(DEFAULT_ITEM) ? DEFAULT_ITEM : items.getFirst());
		YearMonth selectedMonth = parseMonth(month);
		if (selectedMonth == null || !months.contains(selectedMonth)) {
			selectedMonth = months.getFirst();
		}

		model.addAttribute("items", items);
		model.addAttribute("months", months);
		model.addAttribute("selectedItem", selectedItem);
		model.addAttribute("selectedMonth", selectedMonth);
		model.addAttribute("extremes", extremesService.findExtremes(selectedItem, selectedMonth).orElse(null));
		return "extremes";
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
