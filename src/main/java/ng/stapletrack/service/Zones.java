package ng.stapletrack.service;

import ng.stapletrack.entity.Zone;

/** Human-readable names for the geopolitical zones, for display only. */
public final class Zones {

	private Zones() {
	}

	/** NORTH_CENTRAL → "North Central". Null stays null. */
	public static String displayName(Zone zone) {
		if (zone == null) {
			return null;
		}
		return switch (zone) {
			case NORTH_CENTRAL -> "North Central";
			case NORTH_EAST -> "North East";
			case NORTH_WEST -> "North West";
			case SOUTH_EAST -> "South East";
			case SOUTH_SOUTH -> "South South";
			case SOUTH_WEST -> "South West";
		};
	}

}
