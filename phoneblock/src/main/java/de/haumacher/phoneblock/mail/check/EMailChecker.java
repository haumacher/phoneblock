package de.haumacher.phoneblock.mail.check;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checker for disposable e-mail addresses.
 * 
 * @see "https://github.com/disposable-email-domains/disposable-email-domains"
 */
public class EMailChecker {
	
	private static final Logger LOG = LoggerFactory.getLogger(EMailChecker.class);
	
	private static final Set<String> DISPOSABLE_EMAIL_DOMAINS;
	
	static {
		Set<String> domains = new HashSet<>();
		try (BufferedReader in = new BufferedReader(
				new InputStreamReader(
					EMailChecker.class.getResourceAsStream("disposable_email_blocklist.conf"), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				
				domains.add(line);
			}
		} catch (IOException ex) {
			LOG.error("Faild to load list of disposable email domains.", ex);
		}
		DISPOSABLE_EMAIL_DOMAINS = domains;
	}
	
	public static boolean isDisposable(String email) throws AddressException {
		InternetAddress contact = new InternetAddress(email);
		return isDisposable(contact);
	}

	public static boolean isDisposable(InternetAddress contact) throws AddressException {
		String address = contact.getAddress();
		int domainSep = address.indexOf('@');
		String domain = (domainSep >= 0) ? address.substring(domainSep + 1) : address;
		return DISPOSABLE_EMAIL_DOMAINS.contains(domain);
	}
}
