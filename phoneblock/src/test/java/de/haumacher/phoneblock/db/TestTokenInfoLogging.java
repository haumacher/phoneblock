/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests that a token that cannot be parsed is never written to the log (#528).
 */
class TestTokenInfoLogging {

	/** Synthetic token with an invalid Base64 character, so the parse fails. */
	private static final String BROKEN_TOKEN = "pbt_FAKEtokenSECRETvalue-1234567890abcdef";

	@Test
	void parseFailureMessageDoesNotContainToken() {
		Exception ex = assertThrows(Exception.class, () -> TokenInfo.parse(BROKEN_TOKEN));

		String message = TokenInfo.describeParseFailure(BROKEN_TOKEN, ex, 0);

		assertFalse(message.contains(BROKEN_TOKEN), message);
		assertFalse(message.contains(BROKEN_TOKEN.substring(DB.TOKEN_VERSION.length())), message);
		assertFalse(message.contains("FAKEtoken"), message);
		assertFalse(message.contains("SECRETvalue"), message);
		assertTrue(message.contains("length " + BROKEN_TOKEN.length()), message);
		assertTrue(message.contains(ex.getClass().getName()), message);
		assertTrue(message.contains(TokenInfo.fingerprint(BROKEN_TOKEN)), message);
	}

	@Test
	void parseFailureMessageDoesNotEchoExceptionMessage() {
		// An exception message may quote parts of the token (e.g. "Illegal base64 character").
		Exception ex = new IllegalArgumentException("contains FAKEtokenSECRETvalue");

		String message = TokenInfo.describeParseFailure(BROKEN_TOKEN, ex, 24);

		assertFalse(message.contains("FAKEtoken"), message);
		assertTrue(message.contains("24 similar failures suppressed"), message);
	}

	@Test
	void truncatedTokenIsNotLogged() {
		String valid = TokenInfo.createToken(4711, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 });
		String truncated = valid.substring(0, valid.length() - 6);
		Exception ex = assertThrows(Exception.class, () -> TokenInfo.parse(truncated));

		String message = TokenInfo.describeParseFailure(truncated, ex, 0);

		assertFalse(message.contains(truncated.substring(DB.TOKEN_VERSION.length())), message);
	}

	@Test
	void rejectedTokenIsNotLogged() {
		String token = TokenInfo.createToken(4711, new byte[] { 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 1, 2, 3, 4, 5, 6 });

		String message = TokenInfo.describeRejected("Outdated authorization token received", token, 3);

		assertFalse(message.contains(token.substring(DB.TOKEN_VERSION.length())), message);
		assertTrue(message.startsWith("Outdated authorization token received"), message);
		assertTrue(message.contains(TokenInfo.fingerprint(token)), message);
		assertTrue(message.contains("3 similar rejections suppressed"), message);
	}

	@Test
	void fingerprintIsShortAndStable() {
		String fingerprint = TokenInfo.fingerprint(BROKEN_TOKEN);
		assertEquals(8, fingerprint.length());
		assertEquals(fingerprint, TokenInfo.fingerprint(BROKEN_TOKEN));
		assertNotEquals(fingerprint, TokenInfo.fingerprint(BROKEN_TOKEN + "x"));
	}

}
