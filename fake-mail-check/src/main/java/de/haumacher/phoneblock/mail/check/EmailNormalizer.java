package de.haumacher.phoneblock.mail.check;

import java.util.Map;

/**
 * Normalizes e-mail addresses on well-known public domains.
 *
 * <p>
 * Applies provider-specific rules (dot-stripping, plus-addressing, domain aliasing)
 * so that aliases of the same mailbox map to a single canonical address.
 * </p>
 */
public class EmailNormalizer {

	private record Rule(String canonicalDomain, boolean stripDots) {}

	private static final Map<String, Rule> RULES = Map.ofEntries(
		// Gmail
		Map.entry("gmail.com", new Rule("gmail.com", true)),
		Map.entry("googlemail.com", new Rule("gmail.com", true)),

		// Outlook
		Map.entry("outlook.com", new Rule("outlook.com", false)),
		Map.entry("hotmail.com", new Rule("outlook.com", false)),
		Map.entry("live.com", new Rule("outlook.com", false)),
		Map.entry("msn.com", new Rule("outlook.com", false)),

		// Yahoo
		Map.entry("yahoo.com", new Rule("yahoo.com", false)),
		Map.entry("ymail.com", new Rule("yahoo.com", false)),
		Map.entry("rocketmail.com", new Rule("yahoo.com", false)),

		// iCloud
		Map.entry("icloud.com", new Rule("icloud.com", false)),
		Map.entry("me.com", new Rule("icloud.com", false)),
		Map.entry("mac.com", new Rule("icloud.com", false)),

		// Proton
		Map.entry("protonmail.com", new Rule("proton.me", false)),
		Map.entry("proton.me", new Rule("proton.me", false)),
		Map.entry("pm.me", new Rule("proton.me", false))
	);

	/**
	 * Normalizes the given e-mail address if it belongs to a known public domain.
	 *
	 * @param email The raw e-mail address (e.g. "X.Y+foo@googlemail.com").
	 * @return The normalized address (e.g. "xy@gmail.com"), or {@code null} if the
	 *         domain is not a known public domain.
	 */
	public static String normalize(String email) {
		if (email == null) {
			return null;
		}

		int atIndex = email.indexOf('@');
		if (atIndex < 0) {
			return null;
		}

		String localPart = email.substring(0, atIndex);
		String domain = email.substring(atIndex + 1).toLowerCase();

		Rule rule = RULES.get(domain);
		if (rule == null) {
			return null;
		}

		// Strip plus-addressing suffix
		int plusIndex = localPart.indexOf('+');
		if (plusIndex >= 0) {
			localPart = localPart.substring(0, plusIndex);
		}

		// Strip dots if applicable (Gmail)
		if (rule.stripDots) {
			localPart = localPart.replace(".", "");
		}

		localPart = localPart.toLowerCase();

		return localPart + "@" + rule.canonicalDomain;
	}

}
