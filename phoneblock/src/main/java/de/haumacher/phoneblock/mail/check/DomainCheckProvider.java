package de.haumacher.phoneblock.mail.check;

import javax.naming.Context;

import de.haumacher.phoneblock.mail.check.model.DomainCheck;

/**
 * Provider that checks whether an e-mail address is disposable.
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
public interface DomainCheckProvider {

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
	 * Checks the given e-mail address.
	 *
	 * <p>
	 * Providers that only need the domain part are responsible for extracting it themselves.
	 * </p>
	 *
	 * @param email The full e-mail address to check (e.g. "user@example.com").
	 * @return A {@link DomainCheck} result, or {@code null} if this provider cannot answer
	 *         (e.g. due to rate limiting, errors, or missing configuration).
	 */
	DomainCheck checkEmail(String email);

	/**
	 * Checks a normalized e-mail address on a known public domain.
	 *
	 * <p>
	 * This is called for addresses that have been normalized by {@link EmailNormalizer}
	 * (e.g. "xy@gmail.com"). The result is cached in the {@code EMAIL_CHECK} table.
	 * </p>
	 *
	 * @param normalizedEmail The normalized e-mail address.
	 * @return A {@link DomainCheck} result, or {@code null} if this provider cannot answer.
	 */
	default DomainCheck checkNormalizedEmail(String normalizedEmail) {
		return null;
	}

}
