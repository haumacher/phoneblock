/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;

/**
 * Tests {@link PersonalEntries}: phone-ID normalisation from DB format
 * ({@code 0xxx} national, {@code 00xxx} international, optional trailing
 * {@code *} for wildcards) to bare E.164 digits.
 */
class TestPersonalEntries {

	@Test
	void germanNationalExact() {
		Entry e = PersonalEntries.convert("030123456", true);
		assertEquals("4930123456", e.digits());
		assertFalse(e.wildcard());
		assertTrue(e.black());
	}

	@Test
	void germanNationalWildcard() {
		Entry e = PersonalEntries.convert("030*", true);
		assertEquals("4930", e.digits());
		assertTrue(e.wildcard());
	}

	@Test
	void internationalDoubleZeroExact() {
		Entry e = PersonalEntries.convert("0018886749072", false);
		assertEquals("18886749072", e.digits());
		assertFalse(e.wildcard());
		assertFalse(e.black(), "white from the whitelist input list");
	}

	@Test
	void internationalDoubleZeroWildcard() {
		Entry e = PersonalEntries.convert("00188*", false);
		assertEquals("188", e.digits());
		assertTrue(e.wildcard());
	}

	@Test
	void rejectsEmptyAndDegenerate() {
		assertNull(PersonalEntries.convert(null, true));
		assertNull(PersonalEntries.convert("", true));
		assertNull(PersonalEntries.convert("*", true));
	}

	@Test
	void rejectsNonDigitsAfterNormalisation() {
		assertNull(PersonalEntries.convert("030 12 34", true));
		assertNull(PersonalEntries.convert("030-123-456", true));
	}

	@Test
	void rejectsOverlyLongNumber() {
		assertNull(PersonalEntries.convert("01234567890123456", true),
			"15-digit international part overflows MAX_DIGITS once 49 is prepended");
	}

	@Test
	void fromMergesListsWithCorrectColors() {
		List<Entry> result = PersonalEntries.from(
			List.of("030111", "030222*"),
			List.of("030333", "0018886749072"));

		assertEquals(4, result.size());

		assertEquals("4930111", result.get(0).digits());
		assertTrue(result.get(0).black());
		assertFalse(result.get(0).wildcard());

		assertEquals("4930222", result.get(1).digits());
		assertTrue(result.get(1).black());
		assertTrue(result.get(1).wildcard());

		assertEquals("4930333", result.get(2).digits());
		assertFalse(result.get(2).black());

		assertEquals("18886749072", result.get(3).digits());
		assertFalse(result.get(3).black());
	}

	@Test
	void malformedEntriesAreSilentlyDropped() {
		List<Entry> result = PersonalEntries.from(
			List.of("030 12 34", "030111"),
			List.of(""));
		assertEquals(1, result.size());
		assertEquals("4930111", result.get(0).digits());
	}

}
