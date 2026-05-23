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
import de.haumacher.phoneblock.sync.binary.BlocklistLookup.Verdict;

/**
 * Tests {@link BlocklistBinaryAdapter}: phone-string normalisation and
 * end-to-end Blocklist-to-binary-to-lookup round trip.
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
		Blocklist blocklist = Blocklist.create()
			.setVersion(1234L)
			.setNumbers(List.of(
				entry("+4930123456", 5),
				entry("+18886749072", 7),
				entry("+4915112345678", 3)));

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BlocklistBinaryAdapter.write(buf, blocklist);

		BlocklistLookup lookup = BlocklistLookup.of(
			BlocklistBinaryDecoder.read(new ByteArrayInputStream(buf.toByteArray())));

		assertEquals(Verdict.SPAM, lookup.lookup("4930123456"));
		assertEquals(Verdict.SPAM, lookup.lookup("18886749072"));
		assertEquals(Verdict.SPAM, lookup.lookup("4915112345678"));
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4930999999"));
	}

	@Test
	void deletionMarkersAreFiltered() throws IOException {
		Blocklist blocklist = Blocklist.create()
			.setVersion(2L)
			.setNumbers(List.of(
				entry("+4930123456", 5),
				entry("+4930999999", 0)));

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BlocklistBinaryAdapter.write(buf, blocklist);

		BlocklistLookup lookup = BlocklistLookup.of(
			BlocklistBinaryDecoder.read(new ByteArrayInputStream(buf.toByteArray())));

		assertEquals(Verdict.SPAM, lookup.lookup("4930123456"));
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4930999999"));
	}

	@Test
	void malformedEntriesAreSkippedNotFatal() throws IOException {
		Blocklist blocklist = Blocklist.create()
			.setVersion(1L)
			.setNumbers(List.of(
				entry("4930123456", 5),
				entry("+4930111", 4),
				entry("garbage", 3),
				entry("+", 2)));

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BlocklistBinaryAdapter.write(buf, blocklist);

		BlocklistLookup lookup = BlocklistLookup.of(
			BlocklistBinaryDecoder.read(new ByteArrayInputStream(buf.toByteArray())));

		assertEquals(Verdict.SPAM, lookup.lookup("4930111"),
			"the one valid +-prefixed entry survives");
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4930123456"),
			"bare national input is rejected");
	}

	private static BlockListEntry entry(String phone, int votes) {
		return BlockListEntry.create()
			.setPhone(phone)
			.setVotes(votes)
			.setRating(Rating.B_MISSED)
			.setLastActivity(0L);
	}

}
