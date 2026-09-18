package ng.stapletrack.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import ng.stapletrack.service.NationalPriceService;

@Controller
public class HomeController {

	private final NationalPriceService nationalPriceService;

	public HomeController(NationalPriceService nationalPriceService) {
		this.nationalPriceService = nationalPriceService;
	}

	@GetMapping("/")
	public String home(Model model) {
		model.addAttribute("currentPage", "home");
		model.addAttribute("hasData", !nationalPriceService.months().isEmpty());
		return "home";
	}

}
