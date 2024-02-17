package de.haumacher.phoneblock.mail.check;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

public interface EMailChecker {

	default boolean isDisposable(String email) throws AddressException {
		InternetAddress contact = new InternetAddress(email);
		return isDisposable(contact);
	}

	boolean isDisposable(InternetAddress contact) throws AddressException;

}
