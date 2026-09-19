package ng.stapletrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ng.stapletrack.entity.Zone;

class ZonesTests {

	@Test
	void displayNameForEveryZone() {
		assertThat(Zones.displayName(Zone.NORTH_CENTRAL)).isEqualTo("North Central");
		assertThat(Zones.displayName(Zone.NORTH_EAST)).isEqualTo("North East");
		assertThat(Zones.displayName(Zone.NORTH_WEST)).isEqualTo("North West");
		assertThat(Zones.displayName(Zone.SOUTH_EAST)).isEqualTo("South East");
		assertThat(Zones.displayName(Zone.SOUTH_SOUTH)).isEqualTo("South South");
		assertThat(Zones.displayName(Zone.SOUTH_WEST)).isEqualTo("South West");
	}

	@Test
	void everyZoneHasADistinctName() {
		assertThat(java.util.Arrays.stream(Zone.values()).map(Zones::displayName).distinct())
				.hasSize(Zone.values().length)
				.doesNotContainNull();
	}

	@Test
	void keepsNull() {
		assertThat(Zones.displayName(null)).isNull();
	}

}
