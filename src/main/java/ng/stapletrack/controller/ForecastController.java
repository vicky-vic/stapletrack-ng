package ng.stapletrack.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ng.stapletrack.service.forecast.ForecastChart;
import ng.stapletrack.service.forecast.ForecastResult;
import ng.stapletrack.service.forecast.ForecastService;

/** Shows the full-history and recent-trend forecasts side by side. */
@Controller
public class ForecastController {

	static final String DEFAULT_ITEM = "Rice local sold loose";
	static final int MONTHS_AHEAD = 3;

	private final ForecastService forecastService;
	private final ObjectMapper objectMapper;

	public ForecastController(ForecastService forecastService, ObjectMapper objectMapper) {
		this.forecastService = forecastService;
		this.objectMapper = objectMapper;
	}

	/** GET /forecast?item=Yam tuber. A missing or unknown item falls back to the default. */
	@GetMapping("/forecast")
	public String forecast(@RequestParam(required = false) String item, Model model) throws JsonProcessingException {
		model.addAttribute("currentPage", "forecast");

		List<String> items = forecastService.items();
		model.addAttribute("hasData", !items.isEmpty());
		if (items.isEmpty()) {
			return "forecast";
		}
		String selectedItem = item != null && items.contains(item) ? item
				: (items.contains(DEFAULT_ITEM) ? DEFAULT_ITEM : items.getFirst());

		ForecastResult full = forecastService.forecast(selectedItem, MONTHS_AHEAD).orElse(null);
		ForecastResult recent = full == null ? null
				: forecastService.forecastRecent(selectedItem, MONTHS_AHEAD).orElse(null);

		List<ForecastResult> models = new ArrayList<>();
		Optional.ofNullable(full).ifPresent(models::add);
		Optional.ofNullable(recent).ifPresent(models::add);

		model.addAttribute("items", items);
		model.addAttribute("selectedItem", selectedItem);
		model.addAttribute("forecast", full);
		model.addAttribute("recentForecast", recent);
		model.addAttribute("models", models);
		// Serialized with Jackson; the template embeds it as an escaped JS string and JSON.parse()s it
		model.addAttribute("chartJson", full == null ? "null" : objectMapper.writeValueAsString(chart(full, recent)));
		return "forecast";
	}

	/** Actuals from the full history, with both projections on the same axis. */
	private static ForecastChart chart(ForecastResult full, ForecastResult recent) {
		List<ForecastChart.Series> series = new ArrayList<>();
		series.add(new ForecastChart.Series(ForecastChart.Model.FULL, "Full-history forecast", full.forecast()));
		if (recent != null) {
			series.add(new ForecastChart.Series(ForecastChart.Model.RECENT, "Recent-trend forecast", recent.forecast()));
		}
		return ForecastChart.of(full.item(), full.history(), series, ForecastChart.axisFor(full.history()));
	}

}
