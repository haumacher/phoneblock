/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryDecoder;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;
import de.haumacher.phoneblock.sync.binary.BlocklistLookup;
import de.haumacher.phoneblock.sync.binary.BlocklistLookup.Verdict;

/**
 * Tests {@link PersonalEntries}: phone-ID normalisation from DB format
 * ({@code 0xxx} national, {@code 00xxx} international) to bare E.164 digits,
 * with the wildcard distinction coming from the caller — not from the ID.
 */
class TestPersonalEntries {

	@Test
	void germanNationalExact() {
		Entry e = PersonalEntries.convert("030123456", true, false);
		assertEquals("4930123456", e.digits());
		assertFalse(e.wildcard());
		assertTrue(e.black());
	}

	@Test
	void germanNationalWildcard() {
		// The DB shape: bare prefix, wildcard-ness passed in (#514).
		Entry e = PersonalEntries.convert("030", true, true);
		assertEquals("4930", e.digits());
		assertTrue(e.wildcard());
		assertTrue(e.black());
	}

	@Test
	void trailingStarIsToleratedAndImpliesWildcard() {
		Entry e = PersonalEntries.convert("030*", true, false);
		assertEquals("4930", e.digits());
		assertTrue(e.wildcard(), "trailing '*' implies a wildcard even without the flag");
	}

	@Test
	void internationalDoubleZeroExact() {
		Entry e = PersonalEntries.convert("0018886749072", false, false);
		assertEquals("18886749072", e.digits());
		assertFalse(e.wildcard());
		assertFalse(e.black(), "white from the whitelist input list");
	}

	@Test
	void internationalDoubleZeroWildcard() {
		Entry e = PersonalEntries.convert("00188", false, true);
		assertEquals("188", e.digits());
		assertTrue(e.wildcard());
	}

	@Test
	void rejectsEmptyAndDegenerate() {
		assertNull(PersonalEntries.convert(null, true, false));
		assertNull(PersonalEntries.convert("", true, false));
		assertNull(PersonalEntries.convert("*", true, true));
	}

	@Test
	void rejectsNonDigitsAfterNormalisation() {
		assertNull(PersonalEntries.convert("030 12 34", true, false));
		assertNull(PersonalEntries.convert("030-123-456", true, false));
	}

	@Test
	void rejectsOverlyLongNumber() {
		assertNull(PersonalEntries.convert("01234567890123456", true, false),
			"15-digit international part overflows MAX_DIGITS once 49 is prepended");
	}

	@Test
	void fromMergesListsWithCorrectColors() {
		List<Entry> result = PersonalEntries.from(
			List.of("030111"),
			List.of("030333", "0018886749072"),
			List.of("030222"),
			List.of("0049800"));

		assertEquals(5, result.size());

		assertEquals("4930111", result.get(0).digits());
		assertTrue(result.get(0).black());
		assertFalse(result.get(0).wildcard());

		assertEquals("4930333", result.get(1).digits());
		assertFalse(result.get(1).black());
		assertFalse(result.get(1).wildcard());

		assertEquals("18886749072", result.get(2).digits());
		assertFalse(result.get(2).black());

		assertEquals("4930222", result.get(3).digits());
		assertTrue(result.get(3).black());
		assertTrue(result.get(3).wildcard(), "blocked wildcard list must produce prefix entries");

		assertEquals("49800", result.get(4).digits());
		assertFalse(result.get(4).black());
		assertTrue(result.get(4).wildcard(), "allowed wildcard list must produce prefix entries");
	}

	@Test
	void malformedEntriesAreSilentlyDropped() {
		List<Entry> result = PersonalEntries.from(
			List.of("030 12 34", "030111"),
			List.of(""),
			List.of("07 11"),
			List.of());
		assertEquals(1, result.size());
		assertEquals("4930111", result.get(0).digits());
	}

	/**
	 * The wildcards of a {@link DB.PersonalLists} must reach the encoder — the regression guarded
	 * here is #514, where the personal list was built from the exact entries alone and every
	 * dongle got a personal file with an empty prefix section.
	 */
	@Test
	void personalListsCarryWildcardsIntoTheEncoding() throws IOException {
		DB.PersonalLists lists = new DB.PersonalLists(
			List.of("030123456"),
			List.of("030999999"),
			List.of("0900"),
			List.of("0800"));

		BlocklistLookup lookup = encodeAndRead(PersonalEntries.from(lists));

		assertEquals(Verdict.SPAM, lookup.lookup("4930123456"), "exact blacklist entry");
		assertEquals(Verdict.LEGIT, lookup.lookup("4930999999"), "exact whitelist entry");
		assertEquals(Verdict.SPAM, lookup.lookup("49900123456"), "number in a blocked range");
		assertEquals(Verdict.LEGIT, lookup.lookup("49800123456"), "number in an allowed range");
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4930555555"), "unrelated number");
	}

	/**
	 * An exact whitelist entry inside a blocked range keeps its exception: the exact section is
	 * searched before the prefix section.
	 */
	@Test
	void exactWhitelistBeatsBlockedRange() throws IOException {
		DB.PersonalLists lists = new DB.PersonalLists(
			List.of(),
			List.of("0900123456"),
			List.of("0900"),
			List.of());

		BlocklistLookup lookup = encodeAndRead(PersonalEntries.from(lists));

		assertEquals(Verdict.LEGIT, lookup.lookup("49900123456"));
		assertEquals(Verdict.SPAM, lookup.lookup("49900123457"));
	}

	private static BlocklistLookup encodeAndRead(List<Entry> entries) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BlocklistBinaryEncoder.write(out, entries);
		return BlocklistLookup.of(
			BlocklistBinaryDecoder.read(new ByteArrayInputStream(out.toByteArray())));
	}

}
