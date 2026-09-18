package ng.stapletrack.controller;

import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;
import ng.stapletrack.entity.NationalPrice;
import ng.stapletrack.service.NationalPriceService;

@RestController
@RequestMapping("/api/national")
public class NationalPriceController {

	private final NationalPriceService service;

	public NationalPriceController(NationalPriceService service) {
		this.service = service;
	}

	/** GET /api/national?item=Yam tuber&from=2024-01&to=2024-10 */
	@GetMapping
	public List<NationalPrice> history(
			@RequestParam @NotBlank String item,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {
		return service.history(item, from, to);
	}

	/** GET /api/national/month/2024-10 */
	@GetMapping("/month/{monthYear}")
	public List<NationalPrice> forMonth(@PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth monthYear) {
		return service.forMonth(monthYear);
	}

	@GetMapping("/items")
	public List<String> items() {
		return service.items();
	}

	@GetMapping("/months")
	public List<YearMonth> months() {
		return service.months();
	}

}
