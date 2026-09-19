package ng.stapletrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import ng.stapletrack.entity.Zone;

class NigeriaMapRendererTests {

	/** Two states in different zones, plus a non-state outline and a metadata block. */
	private static final String STUB_SVG = """
			<?xml version="1.0" encoding="UTF-8"?>
			<!-- licence comment -->
			<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" aria-label="Map of Nigeria">
				<metadata>provenance blob</metadata>
				<path class="ng-state" data-state="kano" data-state-name="Kano" data-zone="NORTH_WEST" d="M0 0h10v10z"/>
				<path class="ng-state" data-state="lagos" data-state-name="Lagos" data-zone="SOUTH_WEST" d="M20 20h10v10z"/>
				<path class="outline" d="M0 0h100v100z"/>
			</svg>
			""";

	@Test
	void injectsEachZonesColourIntoItsStates() {
		String svg = NigeriaMapRenderer.colorize(STUB_SVG, Map.of(Zone.NORTH_WEST, "#1B4332", Zone.SOUTH_WEST, "#E9A83A"));

		assertThat(stateTag(svg, "Kano")).contains("style=\"fill: #1B4332; stroke: #FFFFFF; stroke-width: 0.5;\"");
		assertThat(stateTag(svg, "Lagos")).contains("style=\"fill: #E9A83A; stroke: #FFFFFF; stroke-width: 0.5;\"");
	}

	@Test
	void zonesWithoutAColourGetTheNeutralFill() {
		String svg = NigeriaMapRenderer.colorize(STUB_SVG, Map.of(Zone.NORTH_WEST, "#1B4332"));

		assertThat(stateTag(svg, "Kano")).contains("fill: #1B4332;");
		assertThat(stateTag(svg, "Lagos")).contains("style=\"fill: var(--st-cream-100); stroke: #FFFFFF; stroke-width: 0.5;\"");
	}

	@Test
	void addsATooltipWithStateZoneAndPrice() {
		String svg = NigeriaMapRenderer.colorize(STUB_SVG, Map.of(Zone.NORTH_WEST, "#1B4332"),
				Map.of(Zone.NORTH_WEST, new BigDecimal("2145.60")));

		assertThat(svg).contains("<title>Kano — North West · ₦2,146</title>");
		assertThat(svg).contains("<title>Lagos — South West</title>");
	}

	@Test
	void leavesNonStateElementsAloneAndStripsMetadataAndPrologue() {
		String svg = NigeriaMapRenderer.colorize(STUB_SVG, Map.of());

		assertThat(svg).startsWith("<svg");
		assertThat(svg).doesNotContain("<metadata", "provenance blob", "<?xml", "licence comment");
		assertThat(svg).containsPattern("<path class=\"outline\" d=\"M0 0h100v100z\"\\s*/>");
	}

	@Test
	void reColouringReplacesThePreviousStyleAndTitle() {
		String once = NigeriaMapRenderer.colorize(STUB_SVG, Map.of(Zone.NORTH_WEST, "#1B4332"));
		String twice = NigeriaMapRenderer.colorize(once, Map.of(Zone.NORTH_WEST, "#E9A83A"));

		assertThat(stateTag(twice, "Kano")).contains("fill: #E9A83A;").doesNotContain("#1B4332");
		assertThat(twice.split("<title>Kano", -1)).hasSize(2);
	}

	@Test
	void rejectsDoctypes() {
		String hostile = "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
				+ "<svg xmlns=\"http://www.w3.org/2000/svg\"><title>&x;</title></svg>";
		assertThatThrownBy(() -> NigeriaMapRenderer.colorize(hostile, Map.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shippedMapHas37StatesAndColoursEveryOne() {
		Map<Zone, String> allZones = new java.util.EnumMap<>(Zone.class);
		for (Zone zone : Zone.values()) {
			allZones.put(zone, "#123456");
		}
		String svg = new NigeriaMapRenderer().render(allZones, Map.of());

		assertThat(Pattern.compile("class=\"ng-state\"").matcher(svg).results().count()).isEqualTo(37);
		assertThat(Pattern.compile("fill: #123456;").matcher(svg).results().count()).isEqualTo(37);
		assertThat(svg).doesNotContain("c2pa");
	}

	/** The opening tag of the state path with the given name. */
	private static String stateTag(String svg, String stateName) {
		Matcher m = Pattern.compile("<path[^>]*data-state-name=\"" + stateName + "\"[^>]*>").matcher(svg);
		assertThat(m.find()).as("path for " + stateName).isTrue();
		return m.group();
	}

}
