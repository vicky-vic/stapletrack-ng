package ng.stapletrack.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ng.stapletrack.service.ExtremesService;
import ng.stapletrack.service.ExtremesService.Extremes;
import ng.stapletrack.service.ExtremesService.StatePair;
import ng.stapletrack.service.ExtremesService.StatePrice;

/** Renders the extremes page against a mocked service — no database needed. */
@WebMvcTest(ExtremesController.class)
class ExtremesControllerTests {

	private static final String RICE = "Rice local sold loose";
	private static final YearMonth OCT_2024 = YearMonth.of(2024, 10);
	private static final YearMonth OCT_2023 = YearMonth.of(2023, 10);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ExtremesService service;

	@BeforeEach
	void setUp() {
		when(service.items()).thenReturn(List.of("Beans brown,sold loose", RICE, "Yam tuber"));
		when(service.months()).thenReturn(List.of(OCT_2024, OCT_2023));
	}

	@Test
	void rendersCardsSpreadAndYearEarlierLine() throws Exception {
		StatePair yearEarlier = new StatePair(OCT_2023, price("Kebbi", "688.00"), price("Lagos", "1122.42"));
		when(service.findExtremes(RICE, OCT_2024)).thenReturn(Optional.of(
				new Extremes(RICE, OCT_2024, price("Benue", "1267.25"), price("Kogi", "2693.41"), yearEarlier)));

		mvc.perform(get("/extremes"))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("<option value=\"Rice local sold loose\" selected=\"selected\">"),
						containsString("<option value=\"2024-10\" selected=\"selected\">Oct 2024</option>"),
						containsString("₦1,267.25"),
						containsString("₦2,693.41"),
						containsString("<strong>2.1×</strong> as much in"),
						containsString("id=\"year-earlier\""),
						containsString("₦688.00"),
						containsString("₦1,122.42"),
						containsString("class=\"nav-link active\" href=\"/extremes\""))));
	}

	@Test
	void hidesYearEarlierLineWhenThereIsNoEarlierData() throws Exception {
		when(service.findExtremes(RICE, OCT_2024)).thenReturn(Optional.of(
				new Extremes(RICE, OCT_2024, price("Benue", "1267.25"), price("Kogi", "2693.41"), null)));

		mvc.perform(get("/extremes"))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString("id=\"price-spread\""),
						not(containsString("id=\"year-earlier\"")))));
	}

	@Test
	void narrowSpreadReadsAsPercentageInTheSentence() throws Exception {
		when(service.findExtremes(RICE, OCT_2024)).thenReturn(Optional.of(
				new Extremes(RICE, OCT_2024, price("Kano", "1000.00"), price("Lagos", "1150.00"), null)));

		mvc.perform(get("/extremes"))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("<strong>15% more</strong> in"),
						not(containsString("as much in")))));
	}

	@Test
	void unknownParametersFallBackToDefaults() throws Exception {
		when(service.findExtremes(RICE, OCT_2024)).thenReturn(Optional.empty());

		mvc.perform(get("/extremes").param("item", "Not an item").param("month", "garbage"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("No extremes for this selection")));
	}

	private static StatePrice price(String state, String price) {
		return new StatePrice(state, new BigDecimal(price));
	}

}
