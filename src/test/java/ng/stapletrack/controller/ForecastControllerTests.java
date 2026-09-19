package ng.stapletrack.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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

import ng.stapletrack.service.forecast.ForecastResult;
import ng.stapletrack.service.forecast.ForecastResult.ForecastMeta;
import ng.stapletrack.service.forecast.ForecastResult.ForecastPoint;
import ng.stapletrack.service.forecast.ForecastResult.PricePoint;
import ng.stapletrack.service.forecast.ForecastService;

/** Renders the forecast page against a mocked service — no database needed. */
@WebMvcTest(ForecastController.class)
class ForecastControllerTests {

	private static final String RICE = "Rice local sold loose";
	private static final String YAM = "Yam tuber";
	private static final String FULL = "Full history (23 months)";
	private static final String RECENT = "Recent trend (last 12 months)";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ForecastService service;

	@BeforeEach
	void setUp() {
		when(service.items()).thenReturn(List.of("Beans brown,sold loose", RICE, YAM));
		when(service.forecast(RICE, 3)).thenReturn(Optional.of(result(RICE, 1900, 0.93, FULL)));
		when(service.forecastRecent(RICE, 3)).thenReturn(Optional.of(result(RICE, 2040, 0.97, RECENT)));
		when(service.forecast(YAM, 3)).thenReturn(Optional.of(result(YAM, 1705, 0.55, FULL)));
		when(service.forecastRecent(YAM, 3)).thenReturn(Optional.of(result(YAM, 1810, 0.40, RECENT)));
	}

	@Test
	void rendersBothModelsWithMetricRowsAndComparison() throws Exception {
		mvc.perform(get("/forecast"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("selectedItem", RICE))
				.andExpect(model().attribute("chartJson", allOf(
						containsString("\"item\":\"Rice local sold loose\""),
						containsString("\"model\":\"FULL\""),
						containsString("\"model\":\"RECENT\""))))
				.andExpect(content().string(allOf(
						containsString("<option value=\"Rice local sold loose\" selected=\"selected\">"),
						containsString(FULL),
						containsString(RECENT),
						containsString("metric-row-full"),
						containsString("metric-row-recent"),
						containsString("0.93"),
						containsString("0.97"),
						containsString("About this forecast"),
						containsString("Two windows, two questions"),
						containsString("class=\"nav-link active\" href=\"/forecast\""))))
				// "The recent trend projects Nov 2024 at ₦2,050. The full history projects ₦1,910. …"
				.andExpect(content().string(matchesPattern(Pattern.compile(
						".*The recent trend projects\\s+<span>Nov 2024</span> at\\s+<strong>₦2,050</strong>\\.\\s+"
								+ "The full history projects\\s+<strong>₦1,910</strong>\\.\\s+"
								+ "Where they disagree, ask which window you trust more\\..*",
						Pattern.DOTALL))));
	}

	@Test
	void selectingAnItemUpdatesTheChartData() throws Exception {
		mvc.perform(get("/forecast").param("item", YAM))
				.andExpect(status().isOk())
				.andExpect(model().attribute("selectedItem", YAM))
				.andExpect(model().attribute("chartJson", allOf(
						containsString("\"item\":\"Yam tuber\""),
						containsString("1705"),
						not(containsString("Rice local sold loose")))))
				.andExpect(content().string(allOf(
						containsString("<option value=\"Yam tuber\" selected=\"selected\">"),
						containsString("weak — take with caution"))));
	}

	@Test
	void unknownItemFallsBackToDefault() throws Exception {
		mvc.perform(get("/forecast").param("item", "Not an item"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("selectedItem", RICE));
	}

	@Test
	void showsMessageWhenTooFewPoints() throws Exception {
		when(service.forecast(RICE, 3)).thenReturn(Optional.empty());

		mvc.perform(get("/forecast"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("forecast", nullValue()))
				.andExpect(content().string(containsString("Not enough data for a forecast")));
	}

	/** Three months of history ending at {@code latest}, then a +10/month forecast. */
	private static ForecastResult result(String item, int latest, double rSquared, String windowLabel) {
		List<PricePoint> history = List.of(
				new PricePoint(YearMonth.of(2024, 8), BigDecimal.valueOf(latest - 20)),
				new PricePoint(YearMonth.of(2024, 9), BigDecimal.valueOf(latest - 10)),
				new PricePoint(YearMonth.of(2024, 10), BigDecimal.valueOf(latest)));
		List<ForecastPoint> forecast = List.of(
				point(YearMonth.of(2024, 11), latest + 10),
				point(YearMonth.of(2024, 12), latest + 20),
				point(YearMonth.of(2025, 1), latest + 30));
		return new ForecastResult(item, history, forecast,
				new ForecastMeta(3, rSquared, "Aug 2024 – Oct 2024", windowLabel));
	}

	private static ForecastPoint point(YearMonth month, int estimate) {
		return new ForecastPoint(month, BigDecimal.valueOf(estimate), BigDecimal.valueOf(estimate - 100),
				BigDecimal.valueOf(estimate + 100));
	}

}
