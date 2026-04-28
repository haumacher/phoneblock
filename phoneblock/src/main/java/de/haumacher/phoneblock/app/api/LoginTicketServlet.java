/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.util.IdentityJwt;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mints a one-shot login ticket that can be redeemed by a browser to start a
 * web session for the API token's owner.
 *
 * <p>
 * Bearer-authenticated. The caller (dongle, mobile app, …) holds a long-lived
 * API token. It exchanges the token for a short-lived ticket, hands the ticket
 * URL to the user's browser, and the long-lived credential never appears in
 * URLs, browser history or referer headers.
 *
 * <p>
 * <b>POST /api/auth/login-ticket</b><br>
 * Body (form-encoded):
 * <ul>
 *   <li>{@code next} — server-relative path the browser should land on after
 *       redemption. Must start with {@code /}; absolute URLs are rejected to
 *       prevent the consumer endpoint from acting as an open redirector.
 * </ul>
 *
 * <p>Response: JSON {@code {"ticket":"<jwt>"}} on success. The caller composes
 * the redemption URL itself by appending the ticket as the {@code t}
 * parameter to {@code <base-url>/auth/login-ticket}.
 */
@WebServlet(urlPatterns = LoginTicketServlet.PATH)
public class LoginTicketServlet extends HttpServlet {

	public static final String PATH = "/api/auth/login-ticket";

	private static final Logger LOG = LoggerFactory.getLogger(LoginTicketServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		String next = req.getParameter("next");
		if (next == null || next.isEmpty()) {
			next = "/";
		}
		if (!next.startsWith("/") || next.startsWith("//")) {
			// Reject scheme-relative ("//evil.example") and absolute URLs so the
			// consumer's redirect always lands on phoneblock.net.
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
					"'next' must be a server-relative path starting with '/'");
			return;
		}

		String ticket = IdentityJwt.signLoginTicket(userName, next);

		LOG.info("Issued login ticket for user {} → {}", userName, next);

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		try (PrintWriter pw = resp.getWriter();
		     JsonWriter w = new JsonWriter(new WriterAdapter(pw))) {
			w.beginObject();
			w.name("ticket"); w.value(ticket);
			w.endObject();
		}
	}
}
