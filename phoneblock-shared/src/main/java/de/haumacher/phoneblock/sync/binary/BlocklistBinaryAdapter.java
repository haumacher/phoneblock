/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.haumacher.phoneblock.app.api.model.BlockListEntry;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;

/**
 * Bridges the JSON-shaped {@link Blocklist} API model and the binary
 * on-device file produced by {@link BlocklistBinaryEncoder}.
 *
 * <p>
 * The adapter is the single point that knows the on-the-wire data:
 * </p>
 * <ul>
 *   <li>Each {@link BlockListEntry} carries the phone number in
 *       international format with a leading {@code +} (or, for legacy
 *       clients, a leading {@code 00}). The leading prefix is stripped so
 *       the encoder sees bare E.164 digits.</li>
 *   <li>The user's personal {@code minVotes} threshold is applied here
 *       &mdash; in the binary format the per-entry vote count is gone, so
 *       the client has no way to filter after the fact.</li>
 *   <li>Personal black/white entries are emitted into the personal section,
 *       independent of any vote threshold.</li>
 * </ul>
 *
 * <p>
 * Wildcard / prefix entries on the community side (from the server's
 * aggregation tables) are not yet produced here &mdash; that lives in a
 * follow-up step.
 * </p>
 */
public final class BlocklistBinaryAdapter {

	private BlocklistBinaryAdapter() {
		// Static utility class.
	}

	/**
	 * Writes the combined community + personal blocklist file to {@code out}.
	 *
	 * @param out             Sink to write to. Not closed by this method.
	 * @param community       Community-list data, as returned by
	 *                        {@code DB.getBlockListAPI()}.
	 * @param personalEntries Personal-list entries (black and/or white). May
	 *                        be empty for users without overrides.
	 * @param minVotes        Per-user minimum vote threshold. Community
	 *                        entries with fewer votes are dropped, since the
	 *                        binary format does not carry vote counts and
	 *                        the dongle cannot filter them itself. Values
	 *                        below {@code 1} are treated as {@code 1}.
	 */
	public static void write(OutputStream out, Blocklist community, Iterable<Entry> personalEntries, int minVotes)
			throws IOException {
		int threshold = Math.max(minVotes, 1);

		List<Entry> communityEntries = new ArrayList<>(community.getNumbers().size());
		for (BlockListEntry row : community.getNumbers()) {
			if (row.getVotes() < threshold) {
				continue;
			}
			String digits = toE164Digits(row.getPhone());
			if (digits == null) {
				continue;
			}
			communityEntries.add(new Entry(digits, false, true));
		}

		BlocklistBinaryEncoder.write(out, communityEntries, personalEntries);
	}

	/**
	 * Convenience overload that writes a community-only file (empty personal
	 * section) and applies the given vote threshold.
	 */
	public static void write(OutputStream out, Blocklist community, int minVotes) throws IOException {
		write(out, community, Collections.emptyList(), minVotes);
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
