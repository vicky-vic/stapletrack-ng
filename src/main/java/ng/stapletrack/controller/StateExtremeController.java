package ng.stapletrack.controller;

import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;
import ng.stapletrack.entity.StateExtreme;
import ng.stapletrack.service.StateExtremeService;

@RestController
@RequestMapping("/api/extremes")
public class StateExtremeController {

	private final StateExtremeService service;

	public StateExtremeController(StateExtremeService service) {
		this.service = service;
	}

	/** GET /api/extremes?item=Yam tuber&month=2024-10 — the HIGHEST and LOWEST state. */
	@GetMapping
	public List<StateExtreme> forMonth(
			@RequestParam @NotBlank String item,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		return service.forMonth(item, month);
	}

	/** GET /api/extremes/history?item=Yam tuber&from=2024-01&to=2024-10 */
	@GetMapping("/history")
	public List<StateExtreme> history(
			@RequestParam @NotBlank String item,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {
		return service.history(item, from, to);
	}

}
