/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the base-11 record encoding in {@link BlocklistRecord}.
 */
class TestBlocklistRecord {

	@Test
	void emptyDigitsEncodeToZero() {
		assertEquals(0L, BlocklistRecord.key(""));
		assertEquals(0, BlocklistRecord.length(0L));
		assertEquals("", BlocklistRecord.digits(0L));
	}

	@Test
	void leadingZeroIsRegularSymbol() {
		long k = BlocklistRecord.key("0");
		assertEquals(BlocklistRecord.POW11[BlocklistRecord.SLOTS - 1], k,
			"'0' is symbol 1 in the most-significant slot");
		assertEquals(1, BlocklistRecord.length(k));
		assertEquals("0", BlocklistRecord.digits(k));
	}

	@Test
	void roundTripDigits() {
		String[] samples = {
			"49",
			"123456789",
			"030123456",
			"0018886749072",
			"1",
			"123456789012345",
		};
		for (String s : samples) {
			long k = BlocklistRecord.key(s);
			assertEquals(s, BlocklistRecord.digits(k), s);
			assertEquals(s.length(), BlocklistRecord.length(k), s);
		}
	}

	@Test
	void terminatorSortsBeforeAnyDigit() {
		long k1 = BlocklistRecord.key("1");
		long k10 = BlocklistRecord.key("10");
		long k12 = BlocklistRecord.key("12");
		long k123 = BlocklistRecord.key("123");
		assertTrue(k1 < k10, "key('1') < key('10') (terminator < '0')");
		assertTrue(k10 < k12, "key('10') < key('12')");
		assertTrue(k12 < k123);
	}

	@Test
	void truncateExtractsPrefix() {
		long full = BlocklistRecord.key("123456");
		assertEquals(BlocklistRecord.key("123"), BlocklistRecord.truncate(full, 3));
		assertEquals(BlocklistRecord.key("1"), BlocklistRecord.truncate(full, 1));
		assertEquals(0L, BlocklistRecord.truncate(full, 0));
		assertEquals(full, BlocklistRecord.truncate(full, BlocklistRecord.SLOTS));
		assertEquals(full, BlocklistRecord.truncate(full, BlocklistRecord.SLOTS + 5));
	}

	@Test
	void recordCombinesKeyAndBlackFlag() {
		long key = BlocklistRecord.key("12345");
		long white = BlocklistRecord.record(key, false);
		long black = BlocklistRecord.record(key, true);

		assertEquals(key, BlocklistRecord.keyOf(white));
		assertEquals(key, BlocklistRecord.keyOf(black));

		assertFalse(BlocklistRecord.isBlack(white));
		assertTrue(BlocklistRecord.isBlack(black));

		assertTrue(white < black,
			"black/white differ in bit 0; black sorts after white for same key");

		long reservedMask = 0b1111_1110L;
		assertEquals(0L, white & reservedMask, "bits 7..1 reserved (zero)");
		assertEquals(0L, black & reservedMask, "bits 7..1 reserved (zero)");
	}

	@Test
	void fifteenDigitsFitInTheKey() {
		String digits = "123456789012345";
		assertEquals(15, digits.length());
		long k = BlocklistRecord.key(digits);
		assertTrue(k >= 0L, "key fits in signed long");
		assertEquals(digits, BlocklistRecord.digits(k));
	}

	@Test
	void rejectsTooManyDigits() {
		assertThrows(IllegalArgumentException.class,
			() -> BlocklistRecord.key("1234567890123456"));
	}

	@Test
	void rejectsNonDigitCharacters() {
		assertThrows(IllegalArgumentException.class, () -> BlocklistRecord.key("+49"));
		assertThrows(IllegalArgumentException.class, () -> BlocklistRecord.key("12 34"));
		assertThrows(IllegalArgumentException.class, () -> BlocklistRecord.key("12-34"));
	}

	@Test
	void maxKeyFitsIn56Bits() {
		String maxDigits = "999999999999999";
		long k = BlocklistRecord.key(maxDigits);
		long limit = 1L << 56;
		assertTrue(Long.compareUnsigned(k, limit) < 0, "key < 2^56");
	}

	@Test
	void recordCanHaveBit63Set() {
		String highDigits = "999999999999999";
		long record = BlocklistRecord.record(BlocklistRecord.key(highDigits), true);
		assertTrue(record < 0L, "shifted record fills bit 63 for high keys");
	}

}
