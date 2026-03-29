/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.dns;

/**
 * Result of an MX DNS lookup for a domain.
 *
 * @param mxHost the MX hostname, or {@code null} if lookup failed
 * @param mxIp the IP address of the MX host, or {@code null} if lookup failed
 */
public record MxResult(String mxHost, String mxIp) {}
