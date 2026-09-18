package ng.stapletrack.controller;

import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;
import ng.stapletrack.entity.ZonalPrice;
import ng.stapletrack.service.ZonalPriceService;

@RestController
@RequestMapping("/api/zonal")
public class ZonalPriceController {

	private final ZonalPriceService service;

	public ZonalPriceController(ZonalPriceService service) {
		this.service = service;
	}

	/** GET /api/zonal?item=Tomato&month=2024-10 — six zones, cheapest first. */
	@GetMapping
	public List<ZonalPrice> rankedForMonth(
			@RequestParam @NotBlank String item,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		return service.rankedForMonth(item, month);
	}

	/** GET /api/zonal/history?item=Tomato&from=2024-01&to=2024-10 */
	@GetMapping("/history")
	public List<ZonalPrice> history(
			@RequestParam @NotBlank String item,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {
		return service.historyAllZones(item, from, to);
	}

}
