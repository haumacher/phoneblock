package de.haumacher.phoneblock.mail.check;

import javax.mail.internet.AddressException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link EMailChecker}.
 */
public class TestEMailChecker {

	@Test
	public void testDisposable() throws AddressException {
		Assertions.assertFalse(EMailChecker.isDisposable("phoneblock@haumacher.de"));
		Assertions.assertTrue(EMailChecker.isDisposable("crazyuser@tonne.to"));
		Assertions.assertTrue(EMailChecker.isDisposable("tonne.to"));
		Assertions.assertFalse(EMailChecker.isDisposable("gmail.com"));
	}
	
}
