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
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryDecoder.DecodedBlocklist;
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
	void endToEndDownloadDecodesToSpamLookup() throws IOException {
		Blocklist blocklist = community(
			entry("+4930123456", 5),
			entry("+18886749072", 7),
			entry("+4915112345678", 3));

		BlocklistLookup community = communityLookup(write(blocklist, List.of(), 1));

		assertEquals(Verdict.SPAM, community.lookup("4930123456"));
		assertEquals(Verdict.SPAM, community.lookup("18886749072"));
		assertEquals(Verdict.SPAM, community.lookup("4915112345678"));
		assertEquals(Verdict.UNKNOWN, community.lookup("4930999999"));
	}

	@Test
	void minVotesDropsLowConfidenceEntries() throws IOException {
		Blocklist blocklist = community(
			entry("+4930111", 10),
			entry("+4930222", 5),
			entry("+4930333", 2));

		BlocklistLookup community = communityLookup(write(blocklist, List.of(), 5));

		assertEquals(Verdict.SPAM, community.lookup("4930111"), "10 votes >= threshold 5");
		assertEquals(Verdict.SPAM, community.lookup("4930222"), "5 votes >= threshold 5");
		assertEquals(Verdict.UNKNOWN, community.lookup("4930333"), "2 votes < threshold 5");
	}

	@Test
	void minVotesBelowOneIsClampedToOne() throws IOException {
		Blocklist blocklist = community(
			entry("+4930111", 1),
			entry("+4930222", 0));

		BlocklistLookup community = communityLookup(write(blocklist, List.of(), 0));

		assertEquals(Verdict.SPAM, community.lookup("4930111"),
			"votes=1 survives clamp threshold 1");
		assertEquals(Verdict.UNKNOWN, community.lookup("4930222"),
			"deletion-marker votes=0 always dropped");
	}

	@Test
	void personalEntriesGoIntoPersonalSection() throws IOException {
		Blocklist blocklist = community(entry("+4930123", 5));
		List<Entry> personal = List.of(
			new Entry("4930123", false, false),
			new Entry("999", true, true));

		DecodedBlocklist decoded = decode(write(blocklist, personal, 1));

		BlocklistLookup community = BlocklistLookup.of(decoded.community());
		BlocklistLookup personalLookup = BlocklistLookup.of(decoded.personal());

		assertEquals(Verdict.SPAM, community.lookup("4930123"));
		assertEquals(Verdict.LEGIT, personalLookup.lookup("4930123"),
			"personal white overrides community black");
		assertEquals(Verdict.SPAM, personalLookup.lookup("9991234"),
			"personal wildcard black hits");
	}

	@Test
	void malformedEntriesAreSkippedNotFatal() throws IOException {
		Blocklist blocklist = community(
			entry("4930123456", 5),
			entry("+4930111", 4),
			entry("garbage", 3),
			entry("+", 2));

		BlocklistLookup community = communityLookup(write(blocklist, List.of(), 1));

		assertEquals(Verdict.SPAM, community.lookup("4930111"),
			"the one valid +-prefixed entry survives");
		assertEquals(Verdict.UNKNOWN, community.lookup("4930123456"),
			"bare national input is rejected");
	}

	private static byte[] write(Blocklist community, List<Entry> personal, int minVotes) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BlocklistBinaryAdapter.write(buf, community, personal, minVotes);
		return buf.toByteArray();
	}

	private static DecodedBlocklist decode(byte[] bytes) throws IOException {
		return BlocklistBinaryDecoder.read(new ByteArrayInputStream(bytes));
	}

	private static BlocklistLookup communityLookup(byte[] bytes) throws IOException {
		return BlocklistLookup.of(decode(bytes).community());
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
