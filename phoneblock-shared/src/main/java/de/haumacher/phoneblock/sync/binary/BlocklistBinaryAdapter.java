/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

/**
 * Small utilities for turning JSON-API phone strings into the bare-E.164
 * digits that {@link BlocklistBinaryEncoder} expects.
 *
 * <p>
 * Lives in {@code phoneblock-shared} so consumers of the JSON
 * {@code /api/blocklist} endpoint can encode entries into the on-device
 * binary form without depending on the server-side
 * {@code NumberAnalyzer}. The full server-side conversion path (raw DB
 * phone IDs, aggregation prefixes, the global whitelist) requires the
 * analyzer and therefore lives in the {@code phoneblock} module.
 * </p>
 */
public final class BlocklistBinaryAdapter {

	private BlocklistBinaryAdapter() {
		// Static utility class.
	}

	/**
	 * Converts a stored phone string into bare E.164 digits, or returns
	 * {@code null} if the input cannot be normalised.
	 *
	 * <p>
	 * Recognised inputs:
	 * </p>
	 * <ul>
	 *   <li>{@code +CC<digits>} &mdash; canonical international, leading {@code +} stripped.</li>
	 *   <li>{@code 00CC<digits>} &mdash; legacy international, leading {@code 00} stripped.</li>
	 * </ul>
	 *
	 * <p>
	 * Anything else &mdash; including bare national numbers, empty input or
	 * strings containing non-digit characters after the prefix &mdash; is
	 * rejected. The blocklist API guarantees the {@code +} form, so this is
	 * mostly a safety net.
	 * </p>
	 */
	public static String toE164Digits(String phone) {
		if (phone == null || phone.isEmpty()) {
			return null;
		}
		String digits;
		if (phone.charAt(0) == '+') {
			digits = phone.substring(1);
		} else if (phone.startsWith("00")) {
			digits = phone.substring(2);
		} else {
			return null;
		}
		if (digits.isEmpty() || digits.length() > BlocklistRecord.MAX_DIGITS) {
			return null;
		}
		for (int i = 0; i < digits.length(); i++) {
			char c = digits.charAt(i);
			if (c < '0' || c > '9') {
				return null;
			}
		}
		return digits;
	}

}
