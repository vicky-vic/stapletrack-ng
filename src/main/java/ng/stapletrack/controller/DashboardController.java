package ng.stapletrack.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ng.stapletrack.service.NationalPriceService;
import ng.stapletrack.service.PriceQueryService;
import ng.stapletrack.service.PriceQueryService.PriceSeries;

@Controller
public class DashboardController {

	private final PriceQueryService priceQueryService;
	private final NationalPriceService nationalPriceService;
	private final ObjectMapper objectMapper;

	public DashboardController(PriceQueryService priceQueryService, NationalPriceService nationalPriceService,
			ObjectMapper objectMapper) {
		this.priceQueryService = priceQueryService;
		this.nationalPriceService = nationalPriceService;
		this.objectMapper = objectMapper;
	}

	@GetMapping("/dashboard")
	public String dashboard(Model model) throws JsonProcessingException {
		List<PriceSeries> series = priceQueryService.dashboardSeries();
		model.addAttribute("currentPage", "dashboard");
		model.addAttribute("hasData", !series.isEmpty());
		model.addAttribute("series", series);
		// Serialized with Jackson; the template embeds it as an escaped JS string and JSON.parse()s it
		model.addAttribute("seriesJson", objectMapper.writeValueAsString(series));
		model.addAttribute("monthCount", nationalPriceService.months().size());
		return "dashboard";
	}

}
