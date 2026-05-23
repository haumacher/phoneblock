/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.app.api.model.BlockListEntry;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;
import de.haumacher.phoneblock.sync.binary.BlocklistLookup.Verdict;

/**
 * Tests {@link BlocklistBinaryAdapter}: phone-string normalisation, vote
 * filtering and the end-to-end Blocklist-to-binary-to-lookup round trip.
 */
class TestBlocklistBinaryAdapter {

	@Test
	void stripsPlusPrefix() {
		assertEquals("4930123456", BlocklistBinaryAdapter.toE164Digits("+4930123456"));
		assertEquals("18886749072", BlocklistBinaryAdapter.toE164Digits("+18886749072"));
	}

	@Test
	void stripsDoubleZeroPrefix() {
		assertEquals("4930123456", BlocklistBinaryAdapter.toE164Digits("004930123456"));
		assertEquals("18886749072", BlocklistBinaryAdapter.toE164Digits("0018886749072"));
	}

	@Test
	void rejectsUnprefixedInput() {
		assertNull(BlocklistBinaryAdapter.toE164Digits("4930123456"),
			"national / unprefixed digit string is not E.164");
		assertNull(BlocklistBinaryAdapter.toE164Digits(""));
		assertNull(BlocklistBinaryAdapter.toE164Digits(null));
		assertNull(BlocklistBinaryAdapter.toE164Digits("+"));
		assertNull(BlocklistBinaryAdapter.toE164Digits("00"));
	}

	@Test
	void rejectsNonDigits() {
		assertNull(BlocklistBinaryAdapter.toE164Digits("+49 30 123456"));
		assertNull(BlocklistBinaryAdapter.toE164Digits("+49-30-123456"));
		assertNull(BlocklistBinaryAdapter.toE164Digits("+49abc"));
	}

	@Test
	void rejectsOverlyLongInput() {
		assertNull(BlocklistBinaryAdapter.toE164Digits("+1234567890123456"));
	}

	@Test
	void endToEndCommunityDownload() throws IOException {
		Blocklist blocklist = community(
			entry("+4930123456", 5),
			entry("+18886749072", 7),
			entry("+4915112345678", 3));

		BlocklistLookup lookup = communityLookup(writeCommunity(blocklist, 1));

		assertEquals(Verdict.SPAM, lookup.lookup("4930123456"));
		assertEquals(Verdict.SPAM, lookup.lookup("18886749072"));
		assertEquals(Verdict.SPAM, lookup.lookup("4915112345678"));
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4930999999"));
	}

	@Test
	void minVotesDropsLowConfidenceEntries() throws IOException {
		Blocklist blocklist = community(
			entry("+4930111", 10),
			entry("+4930222", 5),
			entry("+4930333", 2));

		BlocklistLookup lookup = communityLookup(writeCommunity(blocklist, 5));

		assertEquals(Verdict.SPAM, lookup.lookup("4930111"), "10 votes >= threshold 5");
		assertEquals(Verdict.SPAM, lookup.lookup("4930222"), "5 votes >= threshold 5");
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4930333"), "2 votes < threshold 5");
	}

	@Test
	void minVotesBelowOneIsClampedToOne() throws IOException {
		Blocklist blocklist = community(
			entry("+4930111", 1),
			entry("+4930222", 0));

		BlocklistLookup lookup = communityLookup(writeCommunity(blocklist, 0));

		assertEquals(Verdict.SPAM, lookup.lookup("4930111"),
			"votes=1 survives clamp threshold 1");
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4930222"),
			"deletion-marker votes=0 always dropped");
	}

	@Test
	void personalFilePreservesBlackAndWhite() throws IOException {
		List<Entry> personal = List.of(
			new Entry("4930", true, false),
			new Entry("4930999", true, true),
			new Entry("18886749072", false, false));

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BlocklistBinaryAdapter.writePersonal(buf, personal);
		BlocklistLookup lookup = BlocklistLookup.of(
			BlocklistBinaryDecoder.read(new ByteArrayInputStream(buf.toByteArray())));

		assertEquals(Verdict.SPAM, lookup.lookup("4930999111"),
			"black wildcard wins over outer white wildcard via longest match");
		assertEquals(Verdict.LEGIT, lookup.lookup("4930111"),
			"outer white wildcard catches");
		assertEquals(Verdict.LEGIT, lookup.lookup("18886749072"));
	}

	@Test
	void malformedCommunityEntriesAreSkippedNotFatal() throws IOException {
		Blocklist blocklist = community(
			entry("4930123456", 5),
			entry("+4930111", 4),
			entry("garbage", 3),
			entry("+", 2));

		BlocklistLookup lookup = communityLookup(writeCommunity(blocklist, 1));

		assertEquals(Verdict.SPAM, lookup.lookup("4930111"),
			"the one valid +-prefixed entry survives");
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4930123456"),
			"bare national input is rejected");
	}

	private static byte[] writeCommunity(Blocklist community, int minVotes) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BlocklistBinaryAdapter.writeCommunity(buf, community, minVotes);
		return buf.toByteArray();
	}

	private static BlocklistLookup communityLookup(byte[] bytes) throws IOException {
		return BlocklistLookup.of(BlocklistBinaryDecoder.read(new ByteArrayInputStream(bytes)));
	}

	private static Blocklist community(BlockListEntry... rows) {
		return Blocklist.create()
			.setVersion(1L)
			.setNumbers(List.of(rows));
	}

	private static BlockListEntry entry(String phone, int votes) {
		return BlockListEntry.create()
			.setPhone(phone)
			.setVotes(votes)
			.setRating(Rating.B_MISSED)
			.setLastActivity(0L);
	}

}
