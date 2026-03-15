package de.haumacher.mailcheck;

import javax.naming.Context;

import de.haumacher.mailcheck.model.DomainCheck;

/**
 * Provider that checks whether an e-mail domain or address is disposable.
 *
 * <p>
 * Multiple providers can be chained in the {@link EMailCheckService} orchestrator.
 * The first provider returning a non-{@code null} result wins.
 * </p>
 *
 * <p>
 * Implementations must provide a public constructor taking a {@link Context} parameter
 * to read their own configuration from JNDI. The constructor should throw an exception
 * if required configuration is missing.
 * </p>
 */
public interface EMailCheckProvider {

	/**
	 * A stable identifier for this provider (e.g. "rapidapi").
	 *
	 * <p>
	 * Stored in the {@code SOURCE_SYSTEM} column of the {@code DOMAIN_CHECK} table
	 * to track which provider produced a cached result.
	 * </p>
	 */
	String getProviderId();

	/**
	 * Checks the given domain for disposable e-mail service usage.
	 *
	 * @param domain The domain name to check (e.g. "example.com").
	 * @return A {@link DomainCheck} result, or {@code null} if this provider cannot answer
	 *         (e.g. due to rate limiting, errors, or missing configuration).
	 */
	DomainCheck checkDomain(String domain);

	/**
	 * Checks a full e-mail address on a known public domain.
	 *
	 * <p>
	 * This is called for addresses on well-known public domains (Gmail, Outlook, etc.)
	 * where domain-level checks are insufficient. The orchestrator handles normalization
	 * and caching in the {@code EMAIL_CHECK} table.
	 * </p>
	 *
	 * @param email The original e-mail address (e.g. "x.y+foo@gmail.com").
	 * @return A {@link DomainCheck} result, or {@code null} if this provider cannot answer.
	 */
	default DomainCheck checkEmail(String email) {
		return null;
	}

}
