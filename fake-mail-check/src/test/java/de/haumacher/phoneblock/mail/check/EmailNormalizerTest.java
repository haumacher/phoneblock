package de.haumacher.phoneblock.mail.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import de.haumacher.mailcheck.EmailNormalizer;

class EmailNormalizerTest {

	@Test
	void testGmailDotStripping() {
		assertEquals("xy@gmail.com", EmailNormalizer.normalize("x.y@gmail.com"));
	}

	@Test
	void testGmailPlusAddressing() {
		assertEquals("user@gmail.com", EmailNormalizer.normalize("user+foo@gmail.com"));
	}

	@Test
	void testGmailDotsAndPlus() {
		assertEquals("xy@gmail.com", EmailNormalizer.normalize("x.y+foo@gmail.com"));
	}

	@Test
	void testGooglemailAlias() {
		assertEquals("xy@gmail.com", EmailNormalizer.normalize("x.y+foo@googlemail.com"));
	}

	@Test
	void testOutlookPlusAddressing() {
		assertEquals("user@outlook.com", EmailNormalizer.normalize("user+tag@outlook.com"));
	}

	@Test
	void testHotmailAlias() {
		assertEquals("user@outlook.com", EmailNormalizer.normalize("user+tag@hotmail.com"));
	}

	@Test
	void testLiveAlias() {
		assertEquals("user@outlook.com", EmailNormalizer.normalize("user@live.com"));
	}

	@Test
	void testMsnAlias() {
		assertEquals("user@outlook.com", EmailNormalizer.normalize("user@msn.com"));
	}

	@Test
	void testOutlookDotsPreserved() {
		assertEquals("first.last@outlook.com", EmailNormalizer.normalize("first.last@outlook.com"));
	}

	@Test
	void testYahooPlusAddressing() {
		assertEquals("user@yahoo.com", EmailNormalizer.normalize("user+tag@yahoo.com"));
	}

	@Test
	void testYmailAlias() {
		assertEquals("user@yahoo.com", EmailNormalizer.normalize("user@ymail.com"));
	}

	@Test
	void testRocketmailAlias() {
		assertEquals("user@yahoo.com", EmailNormalizer.normalize("user@rocketmail.com"));
	}

	@Test
	void testIcloudPlusAddressing() {
		assertEquals("user@icloud.com", EmailNormalizer.normalize("user+tag@icloud.com"));
	}

	@Test
	void testMeAlias() {
		assertEquals("user@icloud.com", EmailNormalizer.normalize("user@me.com"));
	}

	@Test
	void testMacAlias() {
		assertEquals("user@icloud.com", EmailNormalizer.normalize("user@mac.com"));
	}

	@Test
	void testProtonmailAlias() {
		assertEquals("user@proton.me", EmailNormalizer.normalize("user@protonmail.com"));
	}

	@Test
	void testProtonMeCanonical() {
		assertEquals("user@proton.me", EmailNormalizer.normalize("user+tag@proton.me"));
	}

	@Test
	void testPmMeAlias() {
		assertEquals("user@proton.me", EmailNormalizer.normalize("user@pm.me"));
	}

	@Test
	void testUnknownDomainReturnsNull() {
		assertNull(EmailNormalizer.normalize("user@example.com"));
	}

	@Test
	void testCaseInsensitive() {
		assertEquals("user@gmail.com", EmailNormalizer.normalize("User@GMAIL.COM"));
	}

	@Test
	void testNoAtSignReturnsNull() {
		assertNull(EmailNormalizer.normalize("invalid-email"));
	}

	@Test
	void testNullReturnsNull() {
		assertNull(EmailNormalizer.normalize(null));
	}

}
