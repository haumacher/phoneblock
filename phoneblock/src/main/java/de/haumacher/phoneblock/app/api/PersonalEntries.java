/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;
import de.haumacher.phoneblock.sync.binary.BlocklistRecord;

/**
 * Converts personal blocklist phone IDs from the DB format ({@code 0xxx} for
 * German national, {@code 00xxx} for international, optional trailing
 * {@code *} for a wildcard prefix) into the bare-E.164 {@link Entry} form
 * expected by the binary encoder.
 *
 * <p>
 * Lives on the server side (not in {@code phoneblock-shared}) because the
 * conversion depends on {@link NumberAnalyzer#toInternationalFormat(String)}.
 * </p>
 */
public final class PersonalEntries {

	private PersonalEntries() {
		// Static utility class.
	}

	/**
	 * Builds the personal-list entries for the binary blocklist.
	 *
	 * @param blacklist Phone IDs the user has explicitly blocked.
	 * @param whitelist Phone IDs the user has explicitly allowed.
	 */
	public static List<Entry> from(Collection<String> blacklist, Collection<String> whitelist) {
		List<Entry> result = new ArrayList<>(blacklist.size() + whitelist.size());
		for (String phoneId : blacklist) {
			Entry e = convert(phoneId, true);
			if (e != null) {
				result.add(e);
			}
		}
		for (String phoneId : whitelist) {
			Entry e = convert(phoneId, false);
			if (e != null) {
				result.add(e);
			}
		}
		return result;
	}

	/**
	 * Normalises a single personal phone ID. Returns {@code null} if the input
	 * cannot be converted to bare E.164 digits.
	 *
	 * @param phoneId DB-format phone ID; may end in {@code *} for a wildcard.
	 * @param black   {@code true} for a blacklist entry, {@code false} for a
	 *                whitelist entry.
	 */
	static Entry convert(String phoneId, boolean black) {
		if (phoneId == null || phoneId.isEmpty()) {
			return null;
		}
		boolean wildcard = phoneId.charAt(phoneId.length() - 1) == '*';
		String stripped = wildcard ? phoneId.substring(0, phoneId.length() - 1) : phoneId;
		if (stripped.isEmpty()) {
			return null;
		}
		String international;
		try {
			international = NumberAnalyzer.toInternationalFormat(stripped);
		} catch (RuntimeException ex) {
			return null;
		}
		if (international == null || international.length() < 2 || international.charAt(0) != '+') {
			return null;
		}
		String digits = international.substring(1);
		if (digits.isEmpty() || digits.length() > BlocklistRecord.MAX_DIGITS) {
			return null;
		}
		for (int i = 0; i < digits.length(); i++) {
			char c = digits.charAt(i);
			if (c < '0' || c > '9') {
				return null;
			}
		}
		return new Entry(digits, wildcard, black);
	}

}
