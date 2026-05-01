/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberBlock;
import de.haumacher.phoneblock.carddav.resource.AddressBookCache.CommonList;

/**
 * Tests 10-15 — personal-layer behaviour:
 * deduplication of personalizations against the common list (10-12)
 * and effectiveness check for exclusions (13-15).
 */
class TestPersonalLayer {

	private static NumberBlock blockOf(String prefix, String... numbers) {
		NumberBlock b = new NumberBlock(prefix, prefix);
		for (String n : numbers) {
			b.add(n);
		}
		return b;
	}

	private static CommonList commonOf(NumberBlock... blocks) {
		return new CommonList(Arrays.asList(blocks));
	}

	private static NumberBlock findById(List<NumberBlock> blocks, String id) {
		for (NumberBlock b : blocks) {
			if (id.equals(b.getBlockId())) {
				return b;
			}
		}
		return null;
	}

	// ===== Tests 10-12: personalization dedup =====

	/**
	 * Test 10: a personalization that already appears as a concrete entry in the
	 * common list does not produce a separate personal singleton — the user only
	 * sees the existing common bucket.
	 */
	@Test
	void personalDedup_concreteOverlap() {
		CommonList common = commonOf(blockOf("+491521", "+491521010", "+491521011"));

		// Simulate the dedup loop from AddressBookCache.computeBlocks().
		java.util.List<NumberBlock> result = new java.util.ArrayList<>(common.blocks());
		String personal = "+491521010"; // already in common
		if (!common.covers(personal)) {
			NumberBlock singleton = new NumberBlock(personal, personal);
			singleton.add(personal);
			result.add(singleton);
		}

		assertEquals(1, result.size(), "no extra singleton bucket for concrete overlap");
		assertNull(findById(result, personal),
			"personal number must not appear as its own bucket id");
	}

	/**
	 * Test 11: a personalization that is covered by a wildcard entry in the common
	 * list is also deduplicated.
	 */
	@Test
	void personalDedup_wildcardCoverage() {
		CommonList common = commonOf(blockOf("+491521", "+491521*"));
		String personal = "+491521987654"; // covered by +491521*

		assertTrue(common.covers(personal), "wildcard must cover the personal number");

		java.util.List<NumberBlock> result = new java.util.ArrayList<>(common.blocks());
		if (!common.covers(personal)) {
			NumberBlock singleton = new NumberBlock(personal, personal);
			singleton.add(personal);
			result.add(singleton);
		}
		assertEquals(1, result.size());
		assertNull(findById(result, personal));
	}

	/**
	 * Test 12: a personalization that is not in the common list at all becomes a
	 * singleton bucket whose id equals the number itself.
	 */
	@Test
	void personalDedup_addsSingletonForNewNumber() {
		CommonList common = commonOf(blockOf("+491521", "+491521010", "+491521011"));
		String personal = "+498912345"; // not in common, no wildcard match

		assertFalse(common.covers(personal));

		java.util.List<NumberBlock> result = new java.util.ArrayList<>(common.blocks());
		if (!common.covers(personal)) {
			NumberBlock singleton = new NumberBlock(personal, personal);
			singleton.add(personal);
			result.add(singleton);
		}

		assertEquals(2, result.size());
		NumberBlock added = findById(result, personal);
		assertNotNull(added, "personal number must appear as its own bucket");
		assertEquals(List.of(personal), added.getNumbers());
		assertEquals(NumberBlock.SPAM_TITLE_PREFIX + personal, added.getBlockTitle());
	}

	// ===== Tests 13-15: exclusion effectiveness =====

	// Exclusions come from the PERSONALIZATION table in DB-id format:
	//   - German numbers as national (leading 0), e.g. "01521010" for "+491521010"
	//   - international numbers as "00" + country code, e.g. "00491521010"
	// hasEffectiveExclusion converts via NumberAnalyzer.toInternationalFormat().

	/** Test 13: an exclusion on a number that is concretely in the common list is effective. */
	@Test
	void exclusionEffectiveness_concreteHit() {
		CommonList common = commonOf(blockOf("+491521", "+491521010", "+491521011"));
		// DB-id "01521010" maps to "+491521010" via toInternationalFormat (German national).
		Set<String> exclusions = Set.of("01521010");
		assertTrue(AddressBookCache.hasEffectiveExclusion(exclusions, common));
	}

	/** Test 14: an exclusion on a number that is not in the common list is a no-op. */
	@Test
	void exclusionEffectiveness_outsideCommon() {
		CommonList common = commonOf(blockOf("+491521", "+491521010", "+491521011"));
		Set<String> exclusions = Set.of("08912345"); // -> "+4908912345", not in common
		assertFalse(AddressBookCache.hasEffectiveExclusion(exclusions, common));
	}

	/** Test 15: an exclusion on a number covered by a wildcard is effective. */
	@Test
	void exclusionEffectiveness_wildcardHit() {
		CommonList common = commonOf(blockOf("+491521", "+491521*"));
		Set<String> exclusions = Set.of("01521987654"); // -> "+491521987654", under wildcard
		assertTrue(AddressBookCache.hasEffectiveExclusion(exclusions, common));
	}

	/** Edge case: empty exclusion set is never effective. */
	@Test
	void exclusionEffectiveness_emptySet() {
		CommonList common = commonOf(blockOf("+491521", "+491521010"));
		assertFalse(AddressBookCache.hasEffectiveExclusion(Collections.emptySet(), common));
	}
}
