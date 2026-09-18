package ng.stapletrack.entity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Nigeria's six geopolitical zones, as labelled in the NBS "ZONE all item" sheet.
 */
public enum Zone {

	NORTH_CENTRAL("NORTH CENTRAL", "NC", "NCENTRAL"),
	NORTH_EAST("NORTH EAST", "NE", "NEAST"),
	NORTH_WEST("NORTH WEST", "NW", "NWEST"),
	SOUTH_EAST("SOUTH EAST", "SE", "SEAST"),
	SOUTH_SOUTH("SOUTH SOUTH", "SS", "SSOUTH"),
	SOUTH_WEST("SOUTH WEST", "SW", "SWEST");

	/** Letters-only upper-case key (e.g. "NORTHCENTRAL", "NC") → zone. */
	private static final Map<String, Zone> BY_KEY = new HashMap<>();

	static {
		for (Zone zone : values()) {
			BY_KEY.put(key(zone.nbsLabel), zone);
			for (String alias : zone.aliases) {
				BY_KEY.put(alias, zone);
			}
		}
	}

	private final String nbsLabel;
	private final String[] aliases;

	Zone(String nbsLabel, String... aliases) {
		this.nbsLabel = nbsLabel;
		this.aliases = aliases;
	}

	public String getNbsLabel() {
		return nbsLabel;
	}

	/**
	 * Matches an NBS column header such as "SOUTH WEST", "South-West", "S/W" or "SW",
	 * ignoring case, spacing and punctuation.
	 */
	public static Optional<Zone> fromNbsLabel(String label) {
		if (label == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_KEY.get(key(label)));
	}

	private static String key(String label) {
		return label.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
	}

}
