/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TestPrefixCheckServlet {

	@Test
	public void testValidHexPrefix() {
		assertTrue(PrefixCheckServlet.isValidHexPrefix("abcd"));
		assertTrue(PrefixCheckServlet.isValidHexPrefix("ABCD"));
		assertTrue(PrefixCheckServlet.isValidHexPrefix("0123"));
		assertTrue(PrefixCheckServlet.isValidHexPrefix("abcde"));
		// Full SHA-1 is also a valid "prefix" (equivalent to an exact lookup).
		assertTrue(PrefixCheckServlet.isValidHexPrefix("0123456789abcdef0123456789abcdef01234567"));
	}

	@Test
	public void testInvalidHexPrefix() {
		assertFalse(PrefixCheckServlet.isValidHexPrefix(null));
		assertFalse(PrefixCheckServlet.isValidHexPrefix(""));
		// Too short to meet the k-anonymity floor.
		assertFalse(PrefixCheckServlet.isValidHexPrefix("abc"));
		// Non-hex character.
		assertFalse(PrefixCheckServlet.isValidHexPrefix("abcg"));
		// Too long (must not exceed a full SHA-1).
		assertFalse(PrefixCheckServlet.isValidHexPrefix("0123456789abcdef0123456789abcdef012345678"));
	}

	@Test
	public void testPrefixLowAlignsToHighBits() {
		byte[] low = PrefixCheckServlet.prefixLow("abcd");
		assertArrayEquals(new byte[] {
			(byte) 0xab, (byte) 0xcd, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0
		}, low);
	}

	@Test
	public void testPrefixLowOddLength() {
		// "abc" occupies 12 bits; the last nibble sits in the high half of byte 1.
		byte[] low = PrefixCheckServlet.prefixLow("abcde");
		assertArrayEquals(new byte[] {
			(byte) 0xab, (byte) 0xcd, (byte) 0xe0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0
		}, low);
	}

	@Test
	public void testPrefixHighIncrementsAtPrefixBoundary() {
		byte[] high = PrefixCheckServlet.prefixHigh("abcd");
		assertArrayEquals(new byte[] {
			(byte) 0xab, (byte) 0xce, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0
		}, high);
	}

	@Test
	public void testPrefixHighCarriesThroughBytes() {
		// "abff" + 1 at the 16-bit boundary = "ac00...".
		byte[] high = PrefixCheckServlet.prefixHigh("abff");
		assertArrayEquals(new byte[] {
			(byte) 0xac, 0, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0
		}, high);
	}

	@Test
	public void testPrefixHighSaturatesOnOverflow() {
		// All-ones prefix: next would exceed 160 bits. Saturate to the 20-byte max.
		byte[] high = PrefixCheckServlet.prefixHigh("ffffffffffffffffffffffffffffffffffffffff");
		for (byte b : high) {
			assertTrue(b == (byte) 0xff);
		}
	}
}
