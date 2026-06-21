/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.app.api.model.BlockListEntry;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.AggregationInfo;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.Ema;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;

/**
 * Tests {@link CommunityEntries}: server-side conversion of exact reports,
 * aggregation prefixes and the global whitelist into the bare-E.164
 * {@link Entry} stream the binary encoder consumes.
 *
 * <p>
 * The wildcard gate is the net-evidence magnitude {@code DB.blockNetVotes}
 * (spam minus legit, decayed to {@code now}). Tests pin {@code now} to
 * {@link Ema#T0_MILLIS}, where the projection factor is 1 and
 * {@code decode(raw) == raw}, so a block's net votes equal
 * {@code round(max(0, spamEvidence - legitEvidence))} directly.
 * </p>
 */
class TestCommunityEntries {

	/** Reference epoch: decode is the identity here, so evidence == votes. */
	private static final long NOW = Ema.T0_MILLIS;

	@Test
	void exactEntriesAreFilteredByMinDirect() {
		Blocklist blocklist = community(
			row("+4930111", 10),
			row("+4930222", 5),
			row("+4930333", 2));

		List<Entry> entries = CommunityEntries.from(blocklist, emptySources(), 5, 5, NOW);

		assertTrue(containsBlackExact(entries, "4930111"));
		assertTrue(containsBlackExact(entries, "4930222"));
		assertEquals(2, entries.size(),
			"votes=2 < minDirect=5 → dropped");
	}

	@Test
	void minDirectBelowOneIsClampedForExactEntries() {
		Blocklist blocklist = community(
			row("+4930111", 1),
			row("+4930222", 0));

		List<Entry> entries = CommunityEntries.from(blocklist, emptySources(), 0, 1, NOW);

		assertEquals(1, entries.size());
		assertTrue(containsBlackExact(entries, "4930111"));
	}

	@Test
	void wildcardsFromBothAggregationLevels() {
		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(
			List.of(agg("030123", 5, 8.0, 0.0)),
			List.of(agg("03012", 3, 12.0, 0.0)),
			Set.of());

		List<Entry> entries = CommunityEntries.from(emptyCommunity(), sources, 1, 1, NOW);

		assertEquals(2, entries.size());
		assertTrue(containsBlackWildcard(entries, "4930123"),
			"10-block prefix is E.164-normalised and wildcard-encoded");
		assertTrue(containsBlackWildcard(entries, "493012"),
			"100-block prefix likewise");
	}

	@Test
	void wildcardsBelowMinRangeAreDropped() {
		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(
			List.of(agg("030123", 5, 8.0, 0.0)),    // net 8
			List.of(agg("03012", 3, 12.0, 0.0)),    // net 12
			Set.of());

		List<Entry> entries = CommunityEntries.from(emptyCommunity(), sources, 1, 10, NOW);

		assertEquals(1, entries.size(), "only the block with net >= minRange=10 survives");
		assertFalse(containsBlackWildcard(entries, "4930123"), "net 8 < 10 → dropped");
		assertTrue(containsBlackWildcard(entries, "493012"), "net 12 >= 10 → kept");
	}

	@Test
	void wildcardNetEvidenceSubtractsLegit() {
		// Contested block: 12 spam, 10 legit → net 2. Excluded at minRange=4,
		// included at minRange=2 — LEGIT_EVIDENCE genuinely moves the gate.
		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(
			List.of(agg("030123", 5, 12.0, 10.0)),
			List.of(),
			Set.of());

		assertEquals(1, CommunityEntries.from(emptyCommunity(), sources, 1, 2, NOW).size(),
			"net 2 >= minRange 2 → kept");
		assertTrue(CommunityEntries.from(emptyCommunity(), sources, 1, 4, NOW).isEmpty(),
			"net 2 < minRange 4 → dropped");
	}

	@Test
	void rangeBlockingDisabledWhenMinRangeBelowOne() {
		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(
			List.of(agg("030123", 5, 99.0, 0.0)),
			List.of(agg("03012", 3, 99.0, 0.0)),
			Set.of());

		List<Entry> entries = CommunityEntries.from(emptyCommunity(), sources, 1, 0, NOW);

		assertTrue(entries.isEmpty(), "minRange < 1 disables wildcard blocking entirely");
	}

	@Test
	void globalWhitelistEmitsExactWhiteEntries() {
		Set<String> whitelist = new HashSet<>(List.of("030555", "0018005551234"));

		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(List.of(), List.of(), whitelist);

		List<Entry> entries = CommunityEntries.from(emptyCommunity(), sources, 1, 1, NOW);

		assertEquals(2, entries.size());
		assertTrue(entries.stream().anyMatch(
			e -> !e.wildcard() && !e.black() && "4930555".equals(e.digits())));
		assertTrue(entries.stream().anyMatch(
			e -> !e.wildcard() && !e.black() && "18005551234".equals(e.digits())));
	}

	@Test
	void whitelistIsNotFilteredByThresholds() {
		// Whitelist entries are explicit user-curated allow-listed numbers; they
		// have no vote count, so the thresholds must not exclude them.
		Set<String> whitelist = Set.of("030555");

		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(List.of(), List.of(), whitelist);

		List<Entry> entries = CommunityEntries.from(emptyCommunity(), sources, 9999, 9999, NOW);

		assertEquals(1, entries.size());
		assertTrue(entries.stream().anyMatch(
			e -> !e.wildcard() && !e.black() && "4930555".equals(e.digits())));
	}

	@Test
	void malformedPhoneIdsAreSkipped() {
		Blocklist blocklist = community(row("garbage", 10));
		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(
			List.of(agg("", 5, 9.0, 0.0)),
			List.of(),
			Set.of(""));

		List<Entry> entries = CommunityEntries.from(blocklist, sources, 1, 1, NOW);

		assertTrue(entries.isEmpty());
	}

	@Test
	void phoneIdToE164DigitsHandlesNationalAndInternational() {
		// National 030123456 has the trunk-prefix 0; toInternationalFormat
		// drops it and prepends +49 → +4930123456 → 4930123456.
		assertEquals("4930123456", CommunityEntries.phoneIdToE164Digits("030123456"));
		// 00-international keeps every digit after the 00.
		assertEquals("18886749072", CommunityEntries.phoneIdToE164Digits("0018886749072"));
		assertNull(CommunityEntries.phoneIdToE164Digits(""));
		assertNull(CommunityEntries.phoneIdToE164Digits(null));
	}

	@Test
	void exactEntriesHelperFiltersByMinDirect() {
		// The capped binary path feeds exactEntries a pre-truncated row list;
		// the minDirect filter is the redundant safety net.
		List<Entry> entries = CommunityEntries.exactEntries(
			List.of(row("+4930111", 10), row("+4930222", 3)), 5);

		assertEquals(1, entries.size());
		assertTrue(containsBlackExact(entries, "4930111"));
		assertFalse(containsBlackExact(entries, "4930222"), "3 votes < minDirect 5 dropped");
	}

	@Test
	void wildcardsAndWhitelistExcludesExactEntries() {
		// The "take all" sections must contain only wildcards + whitelist, never
		// the exact black numbers — those are budgeted separately.
		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(
			List.of(agg("030", 5, 9.0, 0.0)),
			List.of(),
			Set.of("030999"));

		List<Entry> entries = CommunityEntries.wildcardsAndWhitelist(sources, 1, NOW);

		assertEquals(2, entries.size());
		assertTrue(containsBlackWildcard(entries, "4930"), "wildcard prefix present");
		assertTrue(entries.stream().anyMatch(e -> !e.black() && "4930999".equals(e.digits())),
			"whitelist entry present as a white record");
	}

	@Test
	void fromEqualsSplitHelpersCombined() {
		// from() must stay a faithful combiner of the two helpers, so the
		// existing callers/tests keep their meaning after the refactor.
		Blocklist blocklist = community(row("+4930111", 10));
		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(
			List.of(agg("030", 5, 9.0, 0.0)), List.of(), Set.of("030999"));

		List<Entry> combined = CommunityEntries.from(blocklist, sources, 1, 1, NOW);

		List<Entry> manual = CommunityEntries.exactEntries(blocklist.getNumbers(), 1);
		manual.addAll(CommunityEntries.wildcardsAndWhitelist(sources, 1, NOW));

		assertEquals(manual.size(), combined.size());
		assertTrue(containsBlackExact(combined, "4930111"));
		assertTrue(containsBlackWildcard(combined, "4930"));
	}

	private static boolean containsBlackExact(List<Entry> entries, String digits) {
		return entries.stream().anyMatch(e -> !e.wildcard() && e.black() && digits.equals(e.digits()));
	}

	private static boolean containsBlackWildcard(List<Entry> entries, String digits) {
		return entries.stream().anyMatch(e -> e.wildcard() && e.black() && digits.equals(e.digits()));
	}

	/** Aggregation block with explicit spam/legit projected EMAs (heat unused here). */
	private static AggregationInfo agg(String prefix, int cnt, double spamEvidence, double legitEvidence) {
		return new AggregationInfo(prefix, cnt, 0.0, spamEvidence, legitEvidence);
	}

	private static Blocklist community(BlockListEntry... rows) {
		return Blocklist.create()
			.setVersion(1L)
			.setNumbers(List.of(rows));
	}

	private static Blocklist emptyCommunity() {
		return Blocklist.create().setVersion(1L).setNumbers(List.of());
	}

	private static DB.CommunityBinarySources emptySources() {
		return new DB.CommunityBinarySources(List.of(), List.of(), Set.of());
	}

	private static BlockListEntry row(String phone, int votes) {
		return BlockListEntry.create()
			.setPhone(phone)
			.setVotes(votes)
			.setRating(Rating.B_MISSED)
			.setLastActivity(0L);
	}

}
