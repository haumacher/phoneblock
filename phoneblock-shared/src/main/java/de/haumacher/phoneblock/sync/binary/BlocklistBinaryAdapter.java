/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.haumacher.phoneblock.app.api.model.BlockListEntry;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;

/**
 * Bridges the JSON-shaped {@link Blocklist} API model and the binary on-device
 * format produced by {@link BlocklistBinaryEncoder}.
 *
 * <p>
 * Each {@link BlockListEntry} carries the phone number in international format
 * with a leading {@code +} (or, for legacy clients, a leading {@code 00}). The
 * adapter strips that prefix so the encoder sees bare E.164 digits, then emits
 * an exact spam entry for every input row.
 * </p>
 *
 * <p>
 * Wildcard / prefix entries (from the server's aggregation tables) are not yet
 * produced here &mdash; that lives in a follow-up step.
 * </p>
 */
public final class BlocklistBinaryAdapter {

	private BlocklistBinaryAdapter() {
		// Static utility class.
	}

	/**
	 * Writes the given blocklist to {@code out} in binary format.
	 *
	 * <p>
	 * Entries whose phone field cannot be normalised to E.164 digits are
	 * silently skipped &mdash; bad inputs must never abort a download for a
	 * device. Same goes for entries with {@code votes <= 0}, which are deletion
	 * markers that should not appear in a full snapshot but are filtered
	 * defensively.
	 * </p>
	 */
	public static void write(OutputStream out, Blocklist blocklist) throws IOException {
		List<Entry> entries = new ArrayList<>(blocklist.getNumbers().size());
		for (BlockListEntry row : blocklist.getNumbers()) {
			if (row.getVotes() <= 0) {
				continue;
			}
			String digits = toE164Digits(row.getPhone());
			if (digits == null) {
				continue;
			}
			entries.add(new Entry(digits, false, true));
		}
		BlocklistBinaryEncoder.write(out, entries);
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
