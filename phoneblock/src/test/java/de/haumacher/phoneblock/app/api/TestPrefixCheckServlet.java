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
		assertTrue(PrefixCheckServlet.isValidHexPrefix("abcdef"));
		// Full SHA-1 is also a valid "prefix" (equivalent to an exact lookup).
		assertTrue(PrefixCheckServlet.isValidHexPrefix("0123456789abcdef0123456789abcdef01234567"));
	}

	@Test
	public void testInvalidHexPrefix() {
		assertFalse(PrefixCheckServlet.isValidHexPrefix(null));
		assertFalse(PrefixCheckServlet.isValidHexPrefix(""));
		// Too short to meet the k-anonymity floor.
		assertFalse(PrefixCheckServlet.isValidHexPrefix("abc"));
		// Odd length — the prefix must cover whole bytes.
		assertFalse(PrefixCheckServlet.isValidHexPrefix("abcde"));
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
	public void testPrefixLowLongerPrefix() {
		byte[] low = PrefixCheckServlet.prefixLow("abcdef12");
		assertArrayEquals(new byte[] {
			(byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x12, 0,
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
		// "abff" + 1 at the byte boundary ripples carry: "ac00".
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
		// All-ones prefix: every byte overflows and carry ripples off the top.
		// Saturate to the 20-byte max.
		byte[] high = PrefixCheckServlet.prefixHigh("ffffffffffffffffffffffffffffffffffffffff");
		for (byte b : high) {
			assertTrue(b == (byte) 0xff);
		}
	}
}
