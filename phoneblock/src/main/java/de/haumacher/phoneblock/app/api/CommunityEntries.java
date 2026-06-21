/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.BlockListEntry;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.db.AggregationInfo;
import de.haumacher.phoneblock.db.DB.CommunityBinarySources;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryAdapter;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;
import de.haumacher.phoneblock.sync.binary.BlocklistRecord;

/**
 * Builds the full community-list entry stream for the binary blocklist
 * endpoint: exact spam reports, aggregation-driven wildcard prefixes, and
 * the global legitimate-number whitelist.
 *
 * <p>
 * All three sources land in one binary file. The lookup's longest-match rule
 * sorts conflicts out automatically: an exact white whitelist entry beats
 * any wildcard, a longer wildcard beats a shorter one, and within the
 * community list the whitelist is the only source of white entries.
 * </p>
 *
 * <p>
 * The per-user {@code minVotes} threshold gates both exact entries (vote
 * count from the blocklist row, applied here) and wildcards (mapped to a
 * projected {@code SPAM_EVIDENCE} floor, already applied in SQL by
 * {@code DB.getCommunityBinarySources}). The whitelist is emitted unfiltered
 * &mdash; an explicit allow does not care about vote counts.
 * </p>
 */
public final class CommunityEntries {

	private CommunityEntries() {
		// Static utility class.
	}

	/**
	 * Assembles the community-list entries to encode.
	 *
	 * @param exactCommunity   The exact-entry blocklist, as returned by
	 *                         {@code DB.getBlockListAPI()}; entries with
	 *                         votes below {@code minVotes} are dropped.
	 * @param sources          Aggregation candidates and the global whitelist
	 *                         from
	 *                         {@code DB.getCommunityBinarySources(minVotes)};
	 *                         the DB call has already applied the
	 *                         structural and vote thresholds, so the
	 *                         aggregations are taken as-is here.
	 * @param minVotes         Per-user minimum vote threshold. Applied to
	 *                         exact entries (the JSON {@code Blocklist}
	 *                         carries the per-row vote count); values below
	 *                         {@code 1} are clamped.
	 */
	public static List<Entry> from(Blocklist exactCommunity, CommunityBinarySources sources, int minVotes) {
		int threshold = Math.max(minVotes, 1);
		List<Entry> result = new ArrayList<>();

		for (BlockListEntry row : exactCommunity.getNumbers()) {
			if (row.getVotes() < threshold) {
				continue;
			}
			String digits = BlocklistBinaryAdapter.toE164Digits(row.getPhone());
			if (digits == null) {
				continue;
			}
			result.add(new Entry(digits, false, true));
		}

		addAggregations(result, sources.aggregation10());
		addAggregations(result, sources.aggregation100());
		addWhitelist(result, sources.whitelist());

		return result;
	}

	private static void addAggregations(List<Entry> sink, Collection<AggregationInfo> aggregations) {
		for (AggregationInfo a : aggregations) {
			String digits = phoneIdToE164Digits(a.getPrefix());
			if (digits == null) {
				continue;
			}
			sink.add(new Entry(digits, true, true));
		}
	}

	private static void addWhitelist(List<Entry> sink, Set<String> whitelist) {
		for (String phoneId : whitelist) {
			String digits = phoneIdToE164Digits(phoneId);
			if (digits == null) {
				continue;
			}
			sink.add(new Entry(digits, false, false));
		}
	}

	/**
	 * Converts a raw DB phone ID (national {@code 0xxx}, {@code 00}-international,
	 * or aggregation prefix in the same form) into bare E.164 digits.
	 * Returns {@code null} on malformed input or after-prefix garbage.
	 */
	static String phoneIdToE164Digits(String phoneId) {
		if (phoneId == null || phoneId.isEmpty()) {
			return null;
		}
		String international;
		try {
			international = NumberAnalyzer.toInternationalFormat(phoneId);
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
		return digits;
	}

}
