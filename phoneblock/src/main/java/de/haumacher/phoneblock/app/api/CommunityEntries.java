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
import de.haumacher.phoneblock.db.DB;
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
 * Two thresholds gate the spam entries, matching the dongle's own settings:
 * {@code minDirect} ({@code min_direct_votes}) on exact entries' net votes,
 * and {@code minRange} ({@code min_range_votes}) on each wildcard block's
 * net evidence ({@link DB#blockNetVotes}, the same value the live
 * {@code /num} API uses for the wildcard decision). Encoding the dongle's own
 * thresholds is what keeps this download and the dongle's API-fallback
 * verdict identical. The whitelist is emitted unfiltered &mdash; an explicit
 * allow does not care about vote counts.
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
	 *                         {@code DB.getBlockListAPI()} (net, decay-aware
	 *                         votes per #338); entries below {@code minDirect}
	 *                         are dropped.
	 * @param sources          Candidate aggregation blocks and the global
	 *                         whitelist from
	 *                         {@code DB.getCommunityBinarySources(minRange, now)}.
	 *                         The DB call applied the structural gate and a
	 *                         {@code SPAM_EVIDENCE} lower bound; the exact
	 *                         net-evidence test happens here.
	 * @param minDirect        Exact-entry threshold — the dongle's
	 *                         {@code min_direct_votes}. A number is included
	 *                         iff its net votes {@code >= minDirect}. Values
	 *                         below {@code 1} are clamped.
	 * @param minRange         Wildcard threshold — the dongle's
	 *                         {@code min_range_votes}. A block is included iff
	 *                         {@code minRange >= 1} and its
	 *                         {@link DB#blockNetVotes net votes >= minRange},
	 *                         matching the dongle's API-path {@code range_hit}.
	 * @param now              Reference time for decaying the block EMAs, so
	 *                         the net-evidence test matches the live API view.
	 */
	public static List<Entry> from(Blocklist exactCommunity, CommunityBinarySources sources,
			int minDirect, int minRange, long now) {
		int directThreshold = Math.max(minDirect, 1);
		List<Entry> result = new ArrayList<>();

		for (BlockListEntry row : exactCommunity.getNumbers()) {
			if (row.getVotes() < directThreshold) {
				continue;
			}
			String digits = BlocklistBinaryAdapter.toE164Digits(row.getPhone());
			if (digits == null) {
				continue;
			}
			result.add(new Entry(digits, false, true));
		}

		// Wildcards only when range-blocking is on (minRange >= 1), mirroring
		// the dongle API path's `range_hit = (min_range >= 1) && ...` guard.
		if (minRange >= 1) {
			addAggregations(result, sources.aggregation10(), minRange, now);
			addAggregations(result, sources.aggregation100(), minRange, now);
		}
		addWhitelist(result, sources.whitelist());

		return result;
	}

	private static void addAggregations(List<Entry> sink, Collection<AggregationInfo> aggregations,
			int minRange, long now) {
		for (AggregationInfo a : aggregations) {
			// Net-evidence gate (spam − legit), identical to the live
			// /num wildcard decision (DB.computeWildcardVotes), so the
			// download and the API fallback agree.
			if (DB.blockNetVotes(a, now) < minRange) {
				continue;
			}
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
