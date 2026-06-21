/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;

/**
 * Tests {@link CommunityEntries}: server-side conversion of exact reports,
 * aggregation prefixes and the global whitelist into the bare-E.164
 * {@link Entry} stream the binary encoder consumes.
 */
class TestCommunityEntries {

	@Test
	void exactEntriesAreFilteredByMinVotes() {
		Blocklist blocklist = community(
			row("+4930111", 10),
			row("+4930222", 5),
			row("+4930333", 2));

		List<Entry> entries = CommunityEntries.from(blocklist, emptySources(), 5);

		assertTrue(containsBlackExact(entries, "4930111"));
		assertTrue(containsBlackExact(entries, "4930222"));
		assertEquals(2, entries.size(),
			"votes=2 < threshold=5 → dropped");
	}

	@Test
	void minVotesBelowOneIsClampedForExactEntries() {
		Blocklist blocklist = community(
			row("+4930111", 1),
			row("+4930222", 0));

		List<Entry> entries = CommunityEntries.from(blocklist, emptySources(), 0);

		assertEquals(1, entries.size());
		assertTrue(containsBlackExact(entries, "4930111"));
	}

	@Test
	void wildcardsFromBothAggregationLevels() {
		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(
			List.of(new AggregationInfo("030123", 5)),
			List.of(new AggregationInfo("03012", 3)),
			Set.of());

		List<Entry> entries = CommunityEntries.from(emptyCommunity(), sources, 1);

		assertEquals(2, entries.size());
		assertTrue(containsBlackWildcard(entries, "4930123"),
			"10-block prefix is E.164-normalised and wildcard-encoded");
		assertTrue(containsBlackWildcard(entries, "493012"),
			"100-block prefix likewise");
	}

	@Test
	void globalWhitelistEmitsExactWhiteEntries() {
		Set<String> whitelist = new HashSet<>(List.of("030555", "0018005551234"));

		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(List.of(), List.of(), whitelist);

		List<Entry> entries = CommunityEntries.from(emptyCommunity(), sources, 1);

		assertEquals(2, entries.size());
		assertTrue(entries.stream().anyMatch(
			e -> !e.wildcard() && !e.black() && "4930555".equals(e.digits())));
		assertTrue(entries.stream().anyMatch(
			e -> !e.wildcard() && !e.black() && "18005551234".equals(e.digits())));
	}

	@Test
	void whitelistIsNotFilteredByMinVotes() {
		// Whitelist entries are explicit user-curated allow-listed numbers; they
		// have no vote count, so minVotes must not exclude them at any threshold.
		Set<String> whitelist = Set.of("030555");

		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(List.of(), List.of(), whitelist);

		List<Entry> entries = CommunityEntries.from(emptyCommunity(), sources, 9999);

		assertEquals(1, entries.size());
		assertTrue(entries.stream().anyMatch(
			e -> !e.wildcard() && !e.black() && "4930555".equals(e.digits())));
	}

	@Test
	void malformedPhoneIdsAreSkipped() {
		Blocklist blocklist = community(row("garbage", 10));
		DB.CommunityBinarySources sources = new DB.CommunityBinarySources(
			List.of(new AggregationInfo("", 5)),
			List.of(),
			Set.of(""));

		List<Entry> entries = CommunityEntries.from(blocklist, sources, 1);

		assertTrue(entries.isEmpty());
	}

	@Test
	void wildcardVotesThresholdScalesByAggregationFloor() {
		// 10-block floor: MIN_AGGREGATE_10 (= 4) distinct numbers, each at minVotes.
		assertEquals(4 * 1, DB.wildcardVotesThreshold10(1));
		assertEquals(4 * 4, DB.wildcardVotesThreshold10(4));
		assertEquals(4 * 10, DB.wildcardVotesThreshold10(10));

		// 100-block floor: MIN_AGGREGATE_100 * MIN_AGGREGATE_10 (= 3 * 4 = 12)
		// implied numbers, each at minVotes.
		assertEquals(12 * 1, DB.wildcardVotesThreshold100(1));
		assertEquals(12 * 4, DB.wildcardVotesThreshold100(4));
		assertEquals(12 * 10, DB.wildcardVotesThreshold100(10));

		// 100-block threshold is always 3x the 10-block threshold.
		for (int mv : new int[] { 1, 4, 10, 25 }) {
			assertEquals(3 * DB.wildcardVotesThreshold10(mv), DB.wildcardVotesThreshold100(mv),
				"100-block threshold = 3x 10-block at minVotes=" + mv);
		}
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

	private static boolean containsBlackExact(List<Entry> entries, String digits) {
		return entries.stream().anyMatch(e -> !e.wildcard() && e.black() && digits.equals(e.digits()));
	}

	private static boolean containsBlackWildcard(List<Entry> entries, String digits) {
		return entries.stream().anyMatch(e -> e.wildcard() && e.black() && digits.equals(e.digits()));
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
