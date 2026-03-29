package de.haumacher.phoneblock.mail.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import de.haumacher.mailcheck.EmailNormalizer;

class EmailNormalizerTest {

	@Test
	void testGmailDotStripping() {
		assertEquals("xy@gmail.com", EmailNormalizer.toCanonicalPublicAddress("x.y@gmail.com"));
	}

	@Test
	void testGmailPlusAddressing() {
		assertEquals("user@gmail.com", EmailNormalizer.toCanonicalPublicAddress("user+foo@gmail.com"));
	}

	@Test
	void testGmailDotsAndPlus() {
		assertEquals("xy@gmail.com", EmailNormalizer.toCanonicalPublicAddress("x.y+foo@gmail.com"));
	}

	@Test
	void testGooglemailAlias() {
		assertEquals("xy@gmail.com", EmailNormalizer.toCanonicalPublicAddress("x.y+foo@googlemail.com"));
	}

	@Test
	void testOutlookPlusAddressing() {
		assertEquals("user@outlook.com", EmailNormalizer.toCanonicalPublicAddress("user+tag@outlook.com"));
	}

	@Test
	void testHotmailAlias() {
		assertEquals("user@outlook.com", EmailNormalizer.toCanonicalPublicAddress("user+tag@hotmail.com"));
	}

	@Test
	void testLiveAlias() {
		assertEquals("user@outlook.com", EmailNormalizer.toCanonicalPublicAddress("user@live.com"));
	}

	@Test
	void testMsnAlias() {
		assertEquals("user@outlook.com", EmailNormalizer.toCanonicalPublicAddress("user@msn.com"));
	}

	@Test
	void testOutlookDotsPreserved() {
		assertEquals("first.last@outlook.com", EmailNormalizer.toCanonicalPublicAddress("first.last@outlook.com"));
	}

	@Test
	void testYahooPlusAddressing() {
		assertEquals("user@yahoo.com", EmailNormalizer.toCanonicalPublicAddress("user+tag@yahoo.com"));
	}

	@Test
	void testYmailAlias() {
		assertEquals("user@yahoo.com", EmailNormalizer.toCanonicalPublicAddress("user@ymail.com"));
	}

	@Test
	void testRocketmailAlias() {
		assertEquals("user@yahoo.com", EmailNormalizer.toCanonicalPublicAddress("user@rocketmail.com"));
	}

	@Test
	void testIcloudPlusAddressing() {
		assertEquals("user@icloud.com", EmailNormalizer.toCanonicalPublicAddress("user+tag@icloud.com"));
	}

	@Test
	void testMeAlias() {
		assertEquals("user@icloud.com", EmailNormalizer.toCanonicalPublicAddress("user@me.com"));
	}

	@Test
	void testMacAlias() {
		assertEquals("user@icloud.com", EmailNormalizer.toCanonicalPublicAddress("user@mac.com"));
	}

	@Test
	void testProtonmailAlias() {
		assertEquals("user@proton.me", EmailNormalizer.toCanonicalPublicAddress("user@protonmail.com"));
	}

	@Test
	void testProtonMeCanonical() {
		assertEquals("user@proton.me", EmailNormalizer.toCanonicalPublicAddress("user+tag@proton.me"));
	}

	@Test
	void testPmMeAlias() {
		assertEquals("user@proton.me", EmailNormalizer.toCanonicalPublicAddress("user@pm.me"));
	}

	@Test
	void testUnknownDomainReturnsNull() {
		assertNull(EmailNormalizer.toCanonicalPublicAddress("user@example.com"));
	}

	@Test
	void testCaseInsensitive() {
		assertEquals("user@gmail.com", EmailNormalizer.toCanonicalPublicAddress("User@GMAIL.COM"));
	}

	@Test
	void testNoAtSignReturnsNull() {
		assertNull(EmailNormalizer.toCanonicalPublicAddress("invalid-email"));
	}

	@Test
	void testNullReturnsNull() {
		assertNull(EmailNormalizer.toCanonicalPublicAddress(null));
	}

}
