/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

/**
 * Servlet-free render context passed to {@link Resource#propfind} and
 * {@link Resource#fillProperty}. Carries the only request-derived values the
 * render code actually needs, so unit tests can construct a context directly
 * without a {@link jakarta.servlet.http.HttpServletRequest} stub.
 *
 * @param authenticatedUser
 *        Login name of the authenticated user, or {@code null} if no
 *        authentication is in place. Used by
 *        {@code DAV:current-user-principal}.
 */
public record RenderContext(String authenticatedUser) {
}
