/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.db;

import de.haumacher.mailcheck.dns.MxLookup;
import de.haumacher.mailcheck.dns.MxResult;
import de.haumacher.mailcheck.model.DomainStatus;

/**
 * Utility for inserting a new domain into DOMAIN_CHECK with consistent
 * MX resolution and status classification.
 */
public class DomainInsert {

	/**
	 * Resolves the MX record for the given domain and inserts it into DOMAIN_CHECK.
	 *
	 * <p>
	 * If the domain has no valid MX record, it is classified as {@link DomainStatus#INVALID}
	 * with {@code MX_HOST = "-"}. Otherwise it is classified with the given {@code status}.
	 * </p>
	 *
	 * @param domains      The MyBatis mapper.
	 * @param domainName   The domain to insert.
	 * @param status       The status to assign if MX is valid (typically {@link DomainStatus#DISPOSABLE}).
	 * @param now          Timestamp for LAST_CHANGED.
	 * @param sourceSystem Source identifier (e.g. scraper ID, "disposable-list").
	 * @return The resolved {@link MxResult} (may have null host/ip if invalid).
	 */
	public static MxResult insertWithMxLookup(Domains domains, String domainName, DomainStatus status, long now, String sourceSystem) {
		MxResult mx = MxLookup.lookup(domainName);

		if (mx.mxHost() == null) {
			// No valid MX — classify as invalid.
			domains.insertDomain(domainName, DomainStatus.INVALID.protocolName(), now, sourceSystem, "-", null);
		} else {
			domains.insertDomain(domainName, status.protocolName(), now, sourceSystem, mx.mxHost(), mx.mxIp());
		}

		return mx;
	}

}
