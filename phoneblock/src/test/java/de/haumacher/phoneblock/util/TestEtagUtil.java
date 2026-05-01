/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests 16-20 — If-None-Match matching logic, exercised as pure functions.
 */
class TestEtagUtil {

	/** Test 16: a single quoted ETag matches the same value. */
	@Test
	void match_singleEtag() {
		assertTrue(EtagUtil.matchesIfNoneMatch("\"abc\"", "abc"));
	}

	/** Test 17: any entry in a comma-separated list matches. */
	@Test
	void match_listAny() {
		assertTrue(EtagUtil.matchesIfNoneMatch("\"abc\", \"def\"", "abc"));
		assertTrue(EtagUtil.matchesIfNoneMatch("\"abc\", \"def\"", "def"));
		assertFalse(EtagUtil.matchesIfNoneMatch("\"abc\", \"def\"", "ghi"));
	}

	/** Test 18: wildcard {@code *} matches whenever any representation exists. */
	@Test
	void match_wildcard() {
		assertTrue(EtagUtil.matchesIfNoneMatch("*", "abc"));
		assertFalse(EtagUtil.matchesIfNoneMatch("*", null));
	}

	/** Test 19: a stale ETag does not match. */
	@Test
	void match_mismatch() {
		assertFalse(EtagUtil.matchesIfNoneMatch("\"abc\"", "def"));
	}

	/** Test 20: missing quotes do not match (RFC 7232 requires quoted form). */
	@Test
	void match_quoting() {
		// Unquoted entry — must not match a quoted ETag.
		assertFalse(EtagUtil.matchesIfNoneMatch("abc", "abc"));
		// Weak validator prefix is stripped, the rest must still be quoted.
		assertTrue(EtagUtil.matchesIfNoneMatch("W/\"abc\"", "abc"));
		// Embedded double-quote round-trip.
		String etag = "ab\"cd";
		assertEquals("\"ab\\\"cd\"", EtagUtil.quote(etag));
		assertTrue(EtagUtil.matchesIfNoneMatch(EtagUtil.quote(etag), etag));
	}

	/** Edge case: null and empty header values never match. */
	@Test
	void match_nullAndEmpty() {
		assertFalse(EtagUtil.matchesIfNoneMatch(null, "abc"));
		assertFalse(EtagUtil.matchesIfNoneMatch("", "abc"));
		assertFalse(EtagUtil.matchesIfNoneMatch("   ", "abc"));
	}
}
