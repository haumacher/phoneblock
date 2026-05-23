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
 * Bridges the JSON-shaped {@link Blocklist} API model and the binary
 * on-device file produced by {@link BlocklistBinaryEncoder}.
 *
 * <p>
 * Two server-side flows feed the binary format:
 * </p>
 * <ul>
 *   <li>{@link #writeCommunity}: the community blocklist filtered by the
 *       user's {@code minVotes} threshold &mdash; in the binary format the
 *       per-entry vote count is gone, so the client has no way to filter
 *       after the fact.</li>
 *   <li>{@link #writePersonal}: the user's personal black/white entries,
 *       already normalised to {@link Entry} form by the caller.</li>
 * </ul>
 *
 * <p>
 * Wildcard / prefix entries on the community side (from the server's
 * aggregation tables and from the global whitelist) are not yet produced
 * here &mdash; that lives in a follow-up step.
 * </p>
 */
public final class BlocklistBinaryAdapter {

	private BlocklistBinaryAdapter() {
		// Static utility class.
	}

	/**
	 * Writes the community blocklist file to {@code out}.
	 *
	 * @param out       Sink to write to. Not closed by this method.
	 * @param community Community-list data, as returned by
	 *                  {@code DB.getBlockListAPI()}.
	 * @param minVotes  Per-user minimum vote threshold. Entries with fewer
	 *                  votes are dropped, since the binary format does not
	 *                  carry vote counts and the dongle cannot filter them
	 *                  itself. Values below {@code 1} are treated as
	 *                  {@code 1}.
	 */
	public static void writeCommunity(OutputStream out, Blocklist community, int minVotes) throws IOException {
		int threshold = Math.max(minVotes, 1);

		List<Entry> entries = new ArrayList<>(community.getNumbers().size());
		for (BlockListEntry row : community.getNumbers()) {
			if (row.getVotes() < threshold) {
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
	 * Writes the user's personal list file to {@code out}. The caller has
	 * already converted phone IDs into bare-E.164 {@link Entry} form.
	 */
	public static void writePersonal(OutputStream out, Iterable<Entry> personalEntries) throws IOException {
		BlocklistBinaryEncoder.write(out, personalEntries);
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
