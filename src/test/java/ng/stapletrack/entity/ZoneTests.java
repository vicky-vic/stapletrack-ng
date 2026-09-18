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
	void rejectsUnknownLabels() {
		assertThat(Zone.fromNbsLabel("Item Labels")).isEmpty();
		assertThat(Zone.fromNbsLabel(null)).isEmpty();
	}

}
