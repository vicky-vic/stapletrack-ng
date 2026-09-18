package ng.stapletrack.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZoneTests {

	@Test
	void matchesNbsHeaderLabels() {
		assertThat(Zone.fromNbsLabel("SOUTH WEST")).contains(Zone.SOUTH_WEST);
		assertThat(Zone.fromNbsLabel("  north  central ")).contains(Zone.NORTH_CENTRAL);
	}

	@Test
	void matchesAbbreviationsAndPunctuation() {
		assertThat(Zone.fromNbsLabel("North-East")).contains(Zone.NORTH_EAST);
		assertThat(Zone.fromNbsLabel("N/W")).contains(Zone.NORTH_WEST);
		assertThat(Zone.fromNbsLabel("S. South")).contains(Zone.SOUTH_SOUTH);
		assertThat(Zone.fromNbsLabel("SE")).contains(Zone.SOUTH_EAST);
	}

	@Test
	void rejectsUnknownLabels() {
		assertThat(Zone.fromNbsLabel("Item Labels")).isEmpty();
		assertThat(Zone.fromNbsLabel("")).isEmpty();
		assertThat(Zone.fromNbsLabel(null)).isEmpty();
	}

}
