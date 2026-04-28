/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.IdentityJwt;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Public consumer for one-shot login tickets minted by
 * {@link de.haumacher.phoneblock.app.api.LoginTicketServlet}.
 *
 * <p>
 * The user's browser arrives here from a device that knows an API token but
 * does not want to expose it. The ticket carries the subject and the target
 * path, both signed; this servlet establishes a web session for that subject
 * and 302s to the target. The ticket URL is single-use in practice — the
 * subsequent redirect does not include it, so it never lands in browser
 * history under the user's account.
 *
 * <p>
 * <b>GET /auth/login-ticket?t=&lt;jwt&gt;</b>
 */
@WebServlet(urlPatterns = AuthLoginTicketServlet.PATH)
public class AuthLoginTicketServlet extends HttpServlet {

	public static final String PATH = "/auth/login-ticket";

	private static final Logger LOG = LoggerFactory.getLogger(AuthLoginTicketServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String ticket = req.getParameter("t");

		IdentityJwt.Claims claims;
		try {
			claims = IdentityJwt.verify(ticket, IdentityJwt.PURPOSE_LOGIN);
		} catch (IdentityJwt.InvalidTokenException e) {
			LOG.info("login-ticket: rejected: {}", e.getMessage());
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_FORBIDDEN,
					"Anmelde-Ticket ungültig oder abgelaufen.");
			return;
		}

		String next = claims.next;
		if (next == null || next.isEmpty() || !next.startsWith("/") || next.startsWith("//")) {
			// Defense in depth — the mint endpoint already rejects these, but a
			// bad claim must never turn this servlet into an open redirector.
			LOG.info("login-ticket: refusing redirect to '{}' (sub={})", next, claims.sub);
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
					"Ungültiges Weiterleitungsziel.");
			return;
		}

		// Fast path: the browser is already logged in as the same user the
		// ticket attests to. Skip the DB lookup and session reset; the
		// existing session is already exactly what the ticket would create.
		// This endpoint sits outside any LoginFilter, so we read the session
		// attribute directly instead of going through getAuthContext(req).
		HttpSession session = req.getSession(false);
		AuthContext existing = session == null ? null : LoginFilter.getAuthContext(session);
		if (existing != null && claims.sub.equals(existing.getUserName())) {
			LOG.info("login-ticket: shortcut for already-logged-in {} → {}", claims.sub, next);
			resp.sendRedirect(req.getContextPath() + next);
			return;
		}

		DB db = DBService.getInstance();
		AuthContext authContext = db.createMasterLoginToken(claims.sub);
		if (authContext == null) {
			LOG.info("login-ticket: subject '{}' no longer exists", claims.sub);
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_FORBIDDEN,
					"Konto existiert nicht mehr.");
			return;
		}

		LOG.info("login-ticket: opened session for {} → {}", claims.sub, next);
		LoginFilter.setSessionUser(req, authContext);

		resp.sendRedirect(req.getContextPath() + next);
	}
}
