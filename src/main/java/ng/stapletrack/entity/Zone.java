package ng.stapletrack.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Nigeria's six geopolitical zones, as labelled in the NBS "ZONE all item" sheet.
 */
public enum Zone {

	NORTH_CENTRAL("NORTH CENTRAL"),
	NORTH_EAST("NORTH EAST"),
	NORTH_WEST("NORTH WEST"),
	SOUTH_EAST("SOUTH EAST"),
	SOUTH_SOUTH("SOUTH SOUTH"),
	SOUTH_WEST("SOUTH WEST");

	private final String nbsLabel;

	Zone(String nbsLabel) {
		this.nbsLabel = nbsLabel;
	}

	public String getNbsLabel() {
		return nbsLabel;
	}

	/** Matches an NBS column header such as "SOUTH WEST", ignoring case and surrounding whitespace. */
	public static Optional<Zone> fromNbsLabel(String label) {
		if (label == null) {
			return Optional.empty();
		}
		String normalized = label.trim().replaceAll("\\s+", " ");
		return Arrays.stream(values())
				.filter(zone -> zone.nbsLabel.equalsIgnoreCase(normalized))
				.findFirst();
	}

}
