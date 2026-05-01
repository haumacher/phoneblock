/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberBlock;

/**
 * Tests 8 and 9 — content-based, deterministic ETag behaviour.
 */
class TestAddressBookEtag {

	private static NumberBlock blockOf(String prefix, String... numbers) {
		NumberBlock b = new NumberBlock(prefix, prefix);
		for (String n : numbers) {
			b.add(n);
		}
		return b;
	}

	private static AddressBookResource bookOf(int settingsHash, NumberBlock... blocks) {
		return new AddressBookResource("https://x/", "/x/", "/x/", "u", Arrays.asList(blocks), settingsHash);
	}

	// ===== Test 8: Block-ETag =====

	/** Identical members → identical block ETag. */
	@Test
	void blockEtag_stableOnIdenticalMembers() {
		String etag1 = AddressResource.computeEtag(blockOf("+491", "+491521010", "+491521011"));
		String etag2 = AddressResource.computeEtag(blockOf("+491", "+491521010", "+491521011"));
		assertEquals(etag1, etag2);
	}

	/** Member insertion order must not influence the ETag. */
	@Test
	void blockEtag_orderIndependentOnMembers() {
		String etag1 = AddressResource.computeEtag(blockOf("+491", "+491521010", "+491521011"));
		String etag2 = AddressResource.computeEtag(blockOf("+491", "+491521011", "+491521010"));
		assertEquals(etag1, etag2);
	}

	/** A changed member → different ETag. */
	@Test
	void blockEtag_changesOnDifferentMember() {
		String etag1 = AddressResource.computeEtag(blockOf("+491", "+491521010", "+491521011"));
		String etag2 = AddressResource.computeEtag(blockOf("+491", "+491521010", "+491521099"));
		assertNotEquals(etag1, etag2);
	}

	// (The title is content-determined — a separate "title-changes" variant
	// is already covered by blockEtag_changesOnDifferentMember.)

	// ===== Test 9: Collection ETag =====

	/** Identical bucket set → identical collection ETag. */
	@Test
	void collectionEtag_stableOnIdenticalBuckets() {
		AddressBookResource a = bookOf(42,
			blockOf("+491521", "+491521010", "+491521011"),
			blockOf("+493012", "+493012345"));
		AddressBookResource b = bookOf(42,
			blockOf("+491521", "+491521010", "+491521011"),
			blockOf("+493012", "+493012345"));
		assertEquals(a.getEtag(), b.getEtag());
	}

	/** Bucket construction order is irrelevant. */
	@Test
	void collectionEtag_orderIndependentOnBuckets() {
		List<NumberBlock> first = List.of(
			blockOf("+491521", "+491521010"),
			blockOf("+493012", "+493012345"));
		List<NumberBlock> second = List.of(
			blockOf("+493012", "+493012345"),
			blockOf("+491521", "+491521010"));
		AddressBookResource a = new AddressBookResource("https://x/", "/x/", "/x/", "u", first, 42);
		AddressBookResource b = new AddressBookResource("https://x/", "/x/", "/x/", "u", second, 42);
		assertEquals(a.getEtag(), b.getEtag());
	}

	/** Adding a bucket → different collection ETag. */
	@Test
	void collectionEtag_changesOnAddedBucket() {
		AddressBookResource a = bookOf(42, blockOf("+491521", "+491521010"));
		AddressBookResource b = bookOf(42,
			blockOf("+491521", "+491521010"),
			blockOf("+493012", "+493012345"));
		assertNotEquals(a.getEtag(), b.getEtag());
	}

	/** Removing a bucket → different collection ETag. */
	@Test
	void collectionEtag_changesOnRemovedBucket() {
		AddressBookResource a = bookOf(42,
			blockOf("+491521", "+491521010"),
			blockOf("+493012", "+493012345"));
		AddressBookResource b = bookOf(42, blockOf("+491521", "+491521010"));
		assertNotEquals(a.getEtag(), b.getEtag());
	}

	/** Different ListType hash → different collection ETag. */
	@Test
	void collectionEtag_changesOnDifferentSettingsHash() {
		AddressBookResource a = bookOf(42, blockOf("+491521", "+491521010"));
		AddressBookResource b = bookOf(99, blockOf("+491521", "+491521010"));
		assertNotEquals(a.getEtag(), b.getEtag());
	}

	/** Bucket contents change → different collection ETag (delegated through block ETag). */
	@Test
	void collectionEtag_changesOnBucketContentChange() {
		AddressBookResource a = bookOf(42, blockOf("+491521", "+491521010", "+491521011"));
		AddressBookResource b = bookOf(42, blockOf("+491521", "+491521010", "+491521099"));
		assertNotEquals(a.getEtag(), b.getEtag());
	}
}
