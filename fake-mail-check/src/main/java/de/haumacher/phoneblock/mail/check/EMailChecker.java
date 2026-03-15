package de.haumacher.phoneblock.mail.check;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * API for e-mail domain checking.
 * 
 * @see EMailCheckService#getInstance()
 */
public interface EMailChecker {

	/**
	 * Whether the given e-mail is a disposable address.
	 */
	default boolean isDisposable(String email) throws AddressException {
		InternetAddress contact = new InternetAddress(email);
		return isDisposable(contact);
	}

	/**
	 * Whether the given e-mail is a disposable address.
	 */
	boolean isDisposable(InternetAddress contact) throws AddressException;

}
