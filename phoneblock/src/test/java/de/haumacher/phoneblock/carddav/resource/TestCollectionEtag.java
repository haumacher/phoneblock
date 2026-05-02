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
 * Phase-2 tests: input-based collection ETag composition.
 *
 * <p>
 * Verifies that {@link CollectionEtag} produces stable, deterministic
 * hashes from the components that drive a CardDAV collection ETag
 * (block list, personal singletons, settings hash) and that the
 * composition is sensitive to all three of them.
 * </p>
 */
class TestCollectionEtag {

	private static NumberBlock blockOf(String prefix, String... numbers) {
		return new NumberBlock(prefix, Arrays.asList(numbers));
	}

	private static final List<NumberBlock> BLOCKS = List.of(
		blockOf("+491521", "+491521010", "+491521011"),
		blockOf("+493012", "+493012345"));

	@Test
	void hashBlocks_stableOnIdenticalInput() {
		assertEquals(CollectionEtag.hashBlocks(BLOCKS), CollectionEtag.hashBlocks(BLOCKS));
	}

	@Test
	void hashBlocks_orderIndependent() {
		List<NumberBlock> reversed = List.of(BLOCKS.get(1), BLOCKS.get(0));
		assertEquals(CollectionEtag.hashBlocks(BLOCKS), CollectionEtag.hashBlocks(reversed));
	}

	@Test
	void hashBlocks_sensitiveToBlockSet() {
		List<NumberBlock> reduced = List.of(BLOCKS.get(0));
		assertNotEquals(CollectionEtag.hashBlocks(BLOCKS), CollectionEtag.hashBlocks(reduced));
	}

	@Test
	void hashPersonalSingletons_orderIndependent() {
		List<String> a = List.of("+491111", "+492222", "+493333");
		List<String> b = List.of("+493333", "+491111", "+492222");
		assertEquals(CollectionEtag.hashPersonalSingletons(a),
			CollectionEtag.hashPersonalSingletons(b));
	}

	@Test
	void hashPersonalSingletons_emptyIsStable() {
		assertEquals(CollectionEtag.hashPersonalSingletons(List.of()),
			CollectionEtag.hashPersonalSingletons(List.of()));
	}

	@Test
	void hashPersonalSingletons_sensitiveToContent() {
		assertNotEquals(CollectionEtag.hashPersonalSingletons(List.of("+491111")),
			CollectionEtag.hashPersonalSingletons(List.of("+492222")));
	}

	/**
	 * The full-pipeline ETag must be byte-identical to a {@code compose} call
	 * with an empty personal hash and the same blocks/settings, because the
	 * full-pipeline path is just the common-blocks path without personal
	 * singletons.
	 */
	@Test
	void forFullPipeline_equivalentToComposeWithEmptyPersonalHash() {
		String full = CollectionEtag.forFullPipeline(BLOCKS, 42);
		String composed = CollectionEtag.compose(
			CollectionEtag.hashBlocks(BLOCKS),
			CollectionEtag.hashPersonalSingletons(List.of()),
			42);
		assertEquals(full, composed);
	}

	@Test
	void compose_sensitiveToBlocksHash() {
		assertNotEquals(
			CollectionEtag.compose("aaaaaaaaaaaa", "0", 42),
			CollectionEtag.compose("bbbbbbbbbbbb", "0", 42));
	}

	@Test
	void compose_sensitiveToPersonalHash() {
		assertNotEquals(
			CollectionEtag.compose("aaaaaaaaaaaa", "0", 42),
			CollectionEtag.compose("aaaaaaaaaaaa", "1", 42));
	}

	@Test
	void compose_sensitiveToSettingsHash() {
		assertNotEquals(
			CollectionEtag.compose("aaaaaaaaaaaa", "0", 42),
			CollectionEtag.compose("aaaaaaaaaaaa", "0", 43));
	}

	/**
	 * Format invariant: the ETag is a 12-character hex string (48 bits of
	 * SHA-1 prefix), as expected by clients that have already cached an ETag
	 * from before Phase 2 — and so that the wire format stays consistent.
	 */
	@Test
	void format_is12HexChars() {
		String etag = CollectionEtag.forFullPipeline(BLOCKS, 42);
		assertEquals(12, etag.length(), "ETag length: " + etag);
		assertEquals(etag, etag.toLowerCase(), "ETag should be lower-case hex: " + etag);
		etag.chars().forEach(c -> {
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
				throw new AssertionError("Non-hex char in ETag: " + (char) c);
			}
		});
	}
}
