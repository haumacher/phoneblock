/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

/**
 * HTTP entity-tag helpers — kept as pure functions so they can be unit-tested
 * without a servlet container.
 */
public final class EtagUtil {

	private EtagUtil() {
		// no instances
	}

	/**
	 * Wraps the given unquoted ETag value in the quoted form required by RFC 7232,
	 * escaping any embedded double-quote characters.
	 */
	public static String quote(String etag) {
		return '"' + etag.replace("\"", "\\\"") + '"';
	}

	/**
	 * Implements the matching semantics of an {@code If-None-Match} request header against
	 * the given resource ETag (unquoted form, as returned by the resource).
	 *
	 * <p>
	 * Per RFC 7232: a header value of {@code *} matches if any current representation
	 * exists for the resource (i.e. the ETag argument is non-null). A comma-separated
	 * list of quoted ETag values matches if any list entry equals the resource ETag,
	 * ignoring an optional {@code W/} weak-validator prefix.
	 * </p>
	 *
	 * @param headerValue
	 *        Raw {@code If-None-Match} header value, or {@code null}.
	 * @param resourceEtag
	 *        Current ETag of the resource (unquoted), or {@code null} if no representation
	 *        exists.
	 * @return {@code true} if the header value matches the resource ETag — caller should
	 *         then return {@code 304 Not Modified}.
	 */
	public static boolean matchesIfNoneMatch(String headerValue, String resourceEtag) {
		if (headerValue == null) {
			return false;
		}
		String trimmed = headerValue.trim();
		if (trimmed.isEmpty()) {
			return false;
		}
		if ("*".equals(trimmed)) {
			return resourceEtag != null;
		}
		if (resourceEtag == null) {
			return false;
		}
		String quotedEtag = quote(resourceEtag);
		for (String raw : trimmed.split(",")) {
			String entry = raw.trim();
			if (entry.isEmpty()) {
				continue;
			}
			if (entry.startsWith("W/")) {
				entry = entry.substring(2).trim();
			}
			if (entry.equals(quotedEtag)) {
				return true;
			}
		}
		return false;
	}
}
