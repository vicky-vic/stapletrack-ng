package ng.stapletrack.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ng.stapletrack.entity.Zone;
import ng.stapletrack.service.Spread;
import ng.stapletrack.service.ZonesService;
import ng.stapletrack.service.ZonesService.ZonalBreakdown;
import ng.stapletrack.service.ZonesService.ZonePrice;

/** Renders the zones page against a mocked service — no database needed. */
@WebMvcTest(ZonesController.class)
class ZonesControllerTests {

	private static final String RICE = "Rice local sold loose";
	private static final String TOMATO = "Tomato";
	private static final YearMonth OCT_2024 = YearMonth.of(2024, 10);
	private static final YearMonth FEB_2024 = YearMonth.of(2024, 2);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ZonesService service;

	@BeforeEach
	void setUp() {
		when(service.items()).thenReturn(List.of(RICE, TOMATO, "Yam tuber"));
		when(service.months()).thenReturn(List.of(OCT_2024, FEB_2024));
		when(service.findZonalBreakdown(RICE, OCT_2024)).thenReturn(Optional.of(breakdown(RICE, OCT_2024,
				zone(Zone.NORTH_WEST, "North West", "1780.10"), zone(Zone.SOUTH_SOUTH, "South South", "2083.72"))));
		when(service.findZonalBreakdown(TOMATO, FEB_2024)).thenReturn(Optional.of(breakdown(TOMATO, FEB_2024,
				zone(Zone.NORTH_WEST, "North West", "566.96"), zone(Zone.SOUTH_SOUTH, "South South", "1414.73"))));
	}

	@Test
	void rendersDefaultsWithChartMetricsAndSentence() throws Exception {
		mvc.perform(get("/zones"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("selectedItem", RICE))
				.andExpect(model().attribute("selectedMonth", OCT_2024))
				.andExpect(model().attribute("chartJson", allOf(
						containsString("\"labels\":[\"North West\",\"South South\"]"),
						containsString("\"prices\":[1780.10,2083.72]"))))
				.andExpect(content().string(allOf(
						containsString("<option value=\"Rice local sold loose\" selected=\"selected\">"),
						containsString("<option value=\"2024-10\" selected=\"selected\">Oct 2024</option>"),
						containsString("Rice local sold loose across the six zones · Oct 2024"),
						containsString("Cheapest zone"),
						containsString("Most expensive zone"),
						containsString("17% more"),
						containsString("class=\"nav-link active\" href=\"/zones\""))))
				.andExpect(content().string(matchesPattern(Pattern.compile(
						".*In <span>Oct 2024</span>, <span>Rice local sold loose</span>\\s+was cheapest in <strong>North West</strong> at\\s+"
								+ "<span>₦1,780.10</span>\\s+and most expensive in <strong>South South</strong> at\\s+"
								+ "<span>₦2,083.72</span>\\s+— a spread of <strong>17%</strong>\\..*",
						Pattern.DOTALL))));
	}

	@Test
	void selectingItemAndMonthUpdatesTheModel() throws Exception {
		mvc.perform(get("/zones").param("item", TOMATO).param("month", "2024-02"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("selectedItem", TOMATO))
				.andExpect(model().attribute("selectedMonth", FEB_2024))
				.andExpect(model().attribute("chartJson", allOf(containsString("566.96"), not(containsString("1780.10")))))
				.andExpect(content().string(allOf(
						containsString("Tomato across the six zones · Feb 2024"),
						containsString("2.5×"),
						containsString("a spread of <strong>2.5×</strong>"))));
	}

	@Test
	void unknownParametersFallBackToDefaults() throws Exception {
		mvc.perform(get("/zones").param("item", "Not an item").param("month", "garbage"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("selectedItem", RICE))
				.andExpect(model().attribute("selectedMonth", OCT_2024));
	}

	@Test
	void showsMessageWhenSelectionHasNoData() throws Exception {
		when(service.findZonalBreakdown(RICE, FEB_2024)).thenReturn(Optional.empty());

		mvc.perform(get("/zones").param("month", "2024-02"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("No zonal prices for this selection")));
	}

	private static ZonePrice zone(Zone zone, String name, String price) {
		return new ZonePrice(zone, name, new BigDecimal(price));
	}

	private static ZonalBreakdown breakdown(String item, YearMonth month, ZonePrice cheapest, ZonePrice priciest) {
		return new ZonalBreakdown(item, month, List.of(cheapest, priciest), cheapest, priciest,
				Spread.between(cheapest.price(), priciest.price()));
	}

}
