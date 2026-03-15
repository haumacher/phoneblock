/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.dns;

import java.net.InetAddress;
import java.util.Hashtable;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for resolving the MX (mail exchanger) record of a domain.
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
			Hashtable<String, String> env = new Hashtable<>();
			env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
			DirContext ctx = new InitialDirContext(env);

			try {
				Attributes attrs = ctx.getAttributes(domain, new String[] { "MX" });
				Attribute mxAttr = attrs.get("MX");

				if (mxAttr == null || mxAttr.size() == 0) {
					LOG.debug("No MX record found for domain: {}", domain);
					return EMPTY;
				}

				// Find the MX record with the lowest priority.
				String bestHost = null;
				int bestPriority = Integer.MAX_VALUE;

				NamingEnumeration<?> values = mxAttr.getAll();
				while (values.hasMore()) {
					String record = (String) values.next();
					// MX record format: "priority hostname."
					String[] parts = record.trim().split("\\s+", 2);
					if (parts.length < 2) {
						continue;
					}

					int priority;
					try {
						priority = Integer.parseInt(parts[0]);
					} catch (NumberFormatException e) {
						continue;
					}

					if (priority < bestPriority) {
						bestPriority = priority;
						bestHost = parts[1];
					}
				}

				if (bestHost == null) {
					return EMPTY;
				}

				// Strip trailing dot.
				if (bestHost.endsWith(".")) {
					bestHost = bestHost.substring(0, bestHost.length() - 1);
				}

				// Resolve MX host IP.
				String mxIp;
				try {
					mxIp = InetAddress.getByName(bestHost).getHostAddress();
				} catch (Exception e) {
					LOG.debug("Failed to resolve IP for MX host '{}': {}", bestHost, e.getMessage());
					mxIp = null;
				}

				return new MxResult(bestHost, mxIp);
			} finally {
				ctx.close();
			}
		} catch (Exception e) {
			LOG.debug("MX lookup failed for domain '{}': {}", domain, e.getMessage());
			return EMPTY;
		}
	}
}
