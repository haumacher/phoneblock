/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

/**
 * Utility for resolving the MX (mail exchanger) record of a domain using dnsjava.
 */
public class MxLookup {

	private static final Logger LOG = LoggerFactory.getLogger(MxLookup.class);

	private static final MxResult EMPTY = new MxResult(null, null);

	/**
	 * Looks up the MX record for the given domain and resolves the MX host's IP address.
	 *
	 * @param domain the domain to look up (e.g. "mailinator.com")
	 * @return the {@link MxResult} with MX host and IP, or a result with nulls if lookup fails
	 */
	public static MxResult lookup(String domain) {
		try {
			Record[] mxRecords = new Lookup(domain, Type.MX, DClass.IN).run();
			if (mxRecords == null || mxRecords.length == 0) {
				LOG.debug("No MX record found for domain: {}", domain);
				return EMPTY;
			}

			// Find the MX record with the lowest priority.
			MXRecord best = (MXRecord) mxRecords[0];
			for (int i = 1; i < mxRecords.length; i++) {
				MXRecord mx = (MXRecord) mxRecords[i];
				if (mx.getPriority() < best.getPriority()) {
					best = mx;
				}
			}

			String mxHost = best.getTarget().toString(true).toLowerCase();
			if (".".equals(mxHost)) {
				// Domain explicitly declares no mail service.
				return EMPTY;
			}

			// Resolve MX host to IP address.
			String mxIp = null;
			try {
				Record[] aRecords = new Lookup(mxHost, Type.A, DClass.IN).run();
				if (aRecords != null && aRecords.length > 0) {
					mxIp = ((ARecord) aRecords[0]).getAddress().getHostAddress();
				}
			} catch (Exception e) {
				LOG.debug("Failed to resolve IP for MX host '{}': {}", mxHost, e.getMessage());
			}

			return new MxResult(mxHost, mxIp);
		} catch (Exception e) {
			LOG.debug("MX lookup failed for domain '{}': {}", domain, e.getMessage());
			return EMPTY;
		}
	}
}
