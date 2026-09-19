package ng.stapletrack.service;

import java.util.Locale;
import java.util.Map;

/**
 * Canonical spellings for state names that NBS writes differently across releases.
 * Applied when reading for display and comparison; stored rows keep the spelling from their file.
 */
public final class StateNames {

	/** Lower-cased variant → canonical name. Add entries as new variants turn up in NBS files. */
	private static final Map<String, String> ALIASES = Map.of(
			"nassarawa", "Nasarawa");

	private StateNames() {
	}

	/**
	 * Returns the canonical spelling: known variants are mapped, underscores become spaces,
	 * whitespace is collapsed; unknown names pass through otherwise unchanged. Null stays null.
	 */
	public static String canonicalize(String state) {
		if (state == null) {
			return null;
		}
		String cleaned = state.replace('_', ' ').trim().replaceAll("\\s+", " ");
		return ALIASES.getOrDefault(cleaned.toLowerCase(Locale.ROOT), cleaned);
	}

}
