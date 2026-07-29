/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;
import de.haumacher.phoneblock.sync.binary.BlocklistRecord;

/**
 * Converts personal blocklist phone IDs from the DB format ({@code 0xxx} for
 * German national, {@code 00xxx} for international) into the bare-E.164
 * {@link Entry} form expected by the binary encoder.
 *
 * <p>
 * Wildcard prefixes are <em>not</em> recognisable from the phone ID itself:
 * {@code PERSONALIZATION} stores them bare, with the {@code WILDCARD} column
 * carrying the distinction (#377). They are therefore passed in as their own
 * lists (#514) — the source of the bug where the whole prefix section of the
 * dongle's personal list came out empty.
 * </p>
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
	 * Builds the personal-list entries for the binary blocklist from a user's complete personal
	 * lists.
	 *
	 * <p>
	 * Takes the whole {@link DB.PersonalLists} rather than the individual lists so that a caller
	 * cannot forget the wildcard prefixes — dropping them silently turns a blocked range into an
	 * empty prefix section, which is exactly how #514 arose.
	 * </p>
	 */
	public static List<Entry> from(DB.PersonalLists lists) {
		return from(lists.blacklist(), lists.whitelist(),
			lists.blockedWildcards(), lists.allowedWildcards());
	}

	/**
	 * Builds the personal-list entries for the binary blocklist.
	 *
	 * @param blacklist        Exact phone IDs the user has explicitly blocked.
	 * @param whitelist        Exact phone IDs the user has explicitly allowed.
	 * @param blockedWildcards Prefixes (bare, no {@code *}) whose range the user has blocked.
	 * @param allowedWildcards Prefixes (bare, no {@code *}) whose range the user has allowed.
	 */
	static List<Entry> from(Collection<String> blacklist, Collection<String> whitelist,
			Collection<String> blockedWildcards, Collection<String> allowedWildcards) {
		List<Entry> result = new ArrayList<>(blacklist.size() + whitelist.size()
			+ blockedWildcards.size() + allowedWildcards.size());
		convertAll(result, blacklist, true, false);
		convertAll(result, whitelist, false, false);
		convertAll(result, blockedWildcards, true, true);
		convertAll(result, allowedWildcards, false, true);
		return result;
	}

	private static void convertAll(List<Entry> result, Collection<String> phoneIds, boolean black,
			boolean wildcard) {
		for (String phoneId : phoneIds) {
			Entry e = convert(phoneId, black, wildcard);
			if (e != null) {
				result.add(e);
			}
		}
	}

	/**
	 * Normalises a single personal phone ID. Returns {@code null} if the input
	 * cannot be converted to bare E.164 digits.
	 *
	 * @param phoneId  DB-format phone ID. A trailing {@code *} is tolerated (and implies a
	 *                 wildcard) but not what the DB stores — see the class comment.
	 * @param black    {@code true} for a blacklist entry, {@code false} for a
	 *                 whitelist entry.
	 * @param wildcard {@code true} if the ID is a prefix matching a whole number range.
	 */
	static Entry convert(String phoneId, boolean black, boolean wildcard) {
		if (phoneId == null || phoneId.isEmpty()) {
			return null;
		}
		boolean starred = phoneId.charAt(phoneId.length() - 1) == '*';
		String stripped = starred ? phoneId.substring(0, phoneId.length() - 1) : phoneId;
		wildcard |= starred;
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
