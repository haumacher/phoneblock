package de.haumacher.phoneblock.mail.check;

import de.haumacher.phoneblock.mail.check.model.DomainCheck;

/**
 * Provider that checks whether an e-mail domain is disposable.
 *
 * <p>
 * Multiple providers can be chained in the {@link EMailCheckService} orchestrator.
 * The first provider returning a non-{@code null} result wins.
 * </p>
 */
public interface DomainCheckProvider {

	/**
	 * Checks the given domain name.
	 *
	 * @return A {@link DomainCheck} result, or {@code null} if this provider cannot answer
	 *         (e.g. due to rate limiting, errors, or missing configuration).
	 */
	DomainCheck checkDomain(String domainName);

}
