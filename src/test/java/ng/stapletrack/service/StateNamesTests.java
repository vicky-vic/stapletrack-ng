package ng.stapletrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StateNamesTests {

	@Test
	void mapsKnownVariantToCanonicalSpelling() {
		assertThat(StateNames.canonicalize("Nassarawa")).isEqualTo("Nasarawa");
		assertThat(StateNames.canonicalize(" NASSARAWA ")).isEqualTo("Nasarawa");
	}

	@Test
	void passesUnknownNamesThroughUnchanged() {
		assertThat(StateNames.canonicalize("Nasarawa")).isEqualTo("Nasarawa");
		assertThat(StateNames.canonicalize("Lagos")).isEqualTo("Lagos");
		assertThat(StateNames.canonicalize("Akwa Ibom")).isEqualTo("Akwa Ibom");
	}

	@Test
	void tidiesUnderscoresAndSpacing() {
		assertThat(StateNames.canonicalize("Cross_River")).isEqualTo("Cross River");
		assertThat(StateNames.canonicalize("  Akwa   Ibom ")).isEqualTo("Akwa Ibom");
	}

	@Test
	void keepsNull() {
		assertThat(StateNames.canonicalize(null)).isNull();
	}

}
