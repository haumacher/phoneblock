package de.haumacher.mailcheck;

import de.haumacher.mailcheck.model.DomainStatus;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * API for e-mail domain checking.
 *
 * @see EMailCheckService#getInstance()
 */
public interface EMailChecker {

	/**
	 * Checks the status of the given e-mail address.
	 *
	 * @return {@link DomainStatus#DISPOSABLE} if the address or its domain is
	 *         known as disposable, {@link DomainStatus#INVALID} if the domain
	 *         has no valid MX record, {@link DomainStatus#SAFE} otherwise.
	 */
	default DomainStatus check(String email) throws AddressException {
		return check(new InternetAddress(email));
	}

	/**
	 * Checks the status of the given e-mail address.
	 *
	 * @see #check(String)
	 */
	DomainStatus check(InternetAddress contact) throws AddressException;

}
