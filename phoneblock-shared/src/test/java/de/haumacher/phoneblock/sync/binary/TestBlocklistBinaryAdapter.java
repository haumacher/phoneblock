/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link BlocklistBinaryAdapter#toE164Digits(String)} — the small
 * string-normalisation used to turn JSON-API phone strings into bare-E.164
 * digits for the encoder.
 */
class TestBlocklistBinaryAdapter {

	@Test
	void stripsPlusPrefix() {
		assertEquals("4930123456", BlocklistBinaryAdapter.toE164Digits("+4930123456"));
		assertEquals("18886749072", BlocklistBinaryAdapter.toE164Digits("+18886749072"));
	}

	@Test
	void stripsDoubleZeroPrefix() {
		assertEquals("4930123456", BlocklistBinaryAdapter.toE164Digits("004930123456"));
		assertEquals("18886749072", BlocklistBinaryAdapter.toE164Digits("0018886749072"));
	}

	@Test
	void rejectsUnprefixedInput() {
		assertNull(BlocklistBinaryAdapter.toE164Digits("4930123456"),
			"national / unprefixed digit string is not E.164");
		assertNull(BlocklistBinaryAdapter.toE164Digits(""));
		assertNull(BlocklistBinaryAdapter.toE164Digits(null));
		assertNull(BlocklistBinaryAdapter.toE164Digits("+"));
		assertNull(BlocklistBinaryAdapter.toE164Digits("00"));
	}

	@Test
	void rejectsNonDigits() {
		assertNull(BlocklistBinaryAdapter.toE164Digits("+49 30 123456"));
		assertNull(BlocklistBinaryAdapter.toE164Digits("+49-30-123456"));
		assertNull(BlocklistBinaryAdapter.toE164Digits("+49abc"));
	}

	@Test
	void rejectsOverlyLongInput() {
		assertNull(BlocklistBinaryAdapter.toE164Digits("+1234567890123456"));
	}

}
