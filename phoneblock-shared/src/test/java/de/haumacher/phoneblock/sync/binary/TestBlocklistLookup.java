/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;
import de.haumacher.phoneblock.sync.binary.BlocklistLookup.Verdict;

/**
 * Tests the {@link BlocklistLookup} reference implementation, including the
 * longest-match rule and the personal-first composition pattern.
 */
class TestBlocklistLookup {

	@Test
	void exactSpamHit() throws IOException {
		BlocklistLookup lookup = build(new Entry("4930123456", false, true));
		assertEquals(Verdict.SPAM, lookup.lookup("4930123456"));
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4930123457"));
		assertEquals(Verdict.UNKNOWN, lookup.lookup("493012345"), "prefix doesn't match exact");
	}

	@Test
	void exactLegitHit() throws IOException {
		BlocklistLookup lookup = build(new Entry("4930123456", false, false));
		assertEquals(Verdict.LEGIT, lookup.lookup("4930123456"));
	}

	@Test
	void wildcardSpamHitAtMultipleLengths() throws IOException {
		BlocklistLookup lookup = build(
			new Entry("4930", true, true),
			new Entry("100", true, true));
		assertEquals(Verdict.SPAM, lookup.lookup("4930999999"));
		assertEquals(Verdict.SPAM, lookup.lookup("493012"));
		assertEquals(Verdict.SPAM, lookup.lookup("1009999"));
		assertEquals(Verdict.UNKNOWN, lookup.lookup("493"), "shorter than prefix entry");
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4029999"), "different prefix");
	}

	@Test
	void wildcardDoesNotMatchUnrelatedNumber() throws IOException {
		BlocklistLookup lookup = build(new Entry("12345", true, true));
		assertEquals(Verdict.UNKNOWN, lookup.lookup("12344"));
		assertEquals(Verdict.UNKNOWN, lookup.lookup("12"));
		assertEquals(Verdict.SPAM, lookup.lookup("12345"));
		assertEquals(Verdict.SPAM, lookup.lookup("123456789012345"));
	}

	@Test
	void exactBeatsLongerWildcard() throws IOException {
		BlocklistLookup lookup = build(
			new Entry("4930", true, false),
			new Entry("4930123456", false, true));
		assertEquals(Verdict.SPAM, lookup.lookup("4930123456"));
		assertEquals(Verdict.LEGIT, lookup.lookup("4930999999"),
			"under the white prefix, no exact entry");
	}

	@Test
	void longerWildcardBeatsShorterWildcard() throws IOException {
		BlocklistLookup lookup = build(
			new Entry("123", true, false),
			new Entry("12345", true, true));
		assertEquals(Verdict.SPAM, lookup.lookup("123459999"));
		assertEquals(Verdict.SPAM, lookup.lookup("12345"));
		assertEquals(Verdict.LEGIT, lookup.lookup("1239999"));
		assertEquals(Verdict.UNKNOWN, lookup.lookup("99"));
	}

	@Test
	void unknownForEmptyList() throws IOException {
		BlocklistLookup lookup = build();
		assertEquals(Verdict.UNKNOWN, lookup.lookup("4930123456"));
	}

	@Test
	void overlyLongQueryStillMatchesWildcard() throws IOException {
		BlocklistLookup lookup = build(new Entry("4930", true, true));
		assertEquals(Verdict.SPAM, lookup.lookup("493012345678901234567"),
			"21-digit dial (spammer with 6-digit extension) still falls under the 4930* wildcard");
	}

	@Test
	void overlyLongQueryDoesNotForgeExactHit() throws IOException {
		BlocklistLookup lookup = build(new Entry("493012345", false, true));
		assertEquals(Verdict.UNKNOWN, lookup.lookup("493012345999"),
			"exact-9-digit entry must not match a longer dial as exact");
	}

	@Test
	void exactlyFifteenDigitsIsNotTruncated() throws IOException {
		BlocklistLookup lookup = build(new Entry("123456789012345", false, true));
		assertEquals(Verdict.SPAM, lookup.lookup("123456789012345"));
	}

	@Test
	void communityListReturnsSpamOnlyOnHit() throws IOException {
		BlocklistLookup community = build(
			new Entry("4930111", false, true),
			new Entry("0030", true, true));

		assertEquals(Verdict.SPAM, community.lookup("4930111"));
		assertEquals(Verdict.SPAM, community.lookup("003012345"));
		assertEquals(Verdict.UNKNOWN, community.lookup("4930222"));
	}

	@Test
	void personalOverridesCommunity() throws IOException {
		BlocklistLookup community = build(
			new Entry("4930123456", false, true));
		BlocklistLookup personal = build(
			new Entry("4930", true, false));

		assertEquals(Verdict.LEGIT, compose(personal, community, "4930123456"),
			"personal wildcard-white overrides exact community spam");
	}

	@Test
	void personalSpamOverridesCommunityMiss() throws IOException {
		BlocklistLookup community = build(new Entry("0030", true, true));
		BlocklistLookup personal = build(new Entry("4930", true, true));

		assertEquals(Verdict.SPAM, compose(personal, community, "4930111"));
		assertEquals(Verdict.SPAM, compose(personal, community, "003011"));
		assertEquals(Verdict.LEGIT, compose(personal, community, "111222"),
			"no entry anywhere -> LEGIT (default-allow)");
	}

	@Test
	void personalLongestMatchHandlesConflictInPersonalList() throws IOException {
		BlocklistLookup community = build();
		BlocklistLookup personal = build(
			new Entry("4930", true, false),
			new Entry("4930999", true, true));

		assertEquals(Verdict.SPAM, compose(personal, community, "4930999111"),
			"longer black wildcard beats outer white block");
		assertEquals(Verdict.LEGIT, compose(personal, community, "4930111222"),
			"outside the black sub-block: white wildcard wins");
	}

	private static Verdict compose(BlocklistLookup personal, BlocklistLookup community, String digits) {
		Verdict p = personal.lookup(digits);
		if (p != Verdict.UNKNOWN) {
			return p;
		}
		return community.lookup(digits) == Verdict.SPAM ? Verdict.SPAM : Verdict.LEGIT;
	}

	private static BlocklistLookup build(Entry... entries) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BlocklistBinaryEncoder.write(out, List.of(entries));
		return BlocklistLookup.of(BlocklistBinaryDecoder.read(new ByteArrayInputStream(out.toByteArray())));
	}

}
