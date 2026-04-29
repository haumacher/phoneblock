/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.pac4j.core.util.Pac4jConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.util.IdentityJwt;
import de.haumacher.phoneblock.util.LoopbackCallbacks;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Entry point for the "Login with PhoneBlock" SSO flow used by on-prem
 * devices (currently the dongle web UI) to verify a user's PhoneBlock
 * identity without minting an API token.
 *
 * <p>
 * Distinct from the token-issuance flow on purpose: a JWT here just
 * asserts "this user just authenticated against phoneblock.net", which
 * does not grant the calling device any new privileges. So there is no
 * consent screen — once the browser has a session, the JWT is minted
 * and the user is sent straight back to the caller's loopback callback.
 *
 * <p>
 * <b>GET /auth-gate</b><br>
 * Parameters:
 * <ul>
 *   <li>{@code callback}  — loopback URL to redirect to on success.
 *   <li>{@code state}     — opaque CSRF nonce echoed back unchanged.
 *   <li>{@code user_hint} — optional. The PhoneBlock user-name the
 *        caller expects to be authenticated. When the existing browser
 *        session is for a <em>different</em> user, this servlet
 *        silently logs that session out and bounces back to itself,
 *        so the user lands on the standard sign-in form instead of a
 *        "wrong account" mint → mismatch → retry → mint loop.
 * </ul>
 *
 * <p>
 * Behaviour:
 * <ul>
 *   <li>Authenticated, no hint or hint matches → 302 to
 *       {@code <callback>?code=<jwt>&state=<state>}.
 *   <li>Authenticated, hint differs → 302 to {@code /logout?url=…}
 *       pointing back at this servlet. After pac4j's session cleanup
 *       and redirect we re-enter with no session and fall through to
 *       the unauthenticated path.
 *   <li>Unauthenticated → bounced through {@link LoginServlet}; after
 *       login the user lands back here and gets the same redirect.
 * </ul>
 */
@WebServlet(urlPatterns = AuthGateServlet.PATH)
public class AuthGateServlet extends HttpServlet {

	public static final String PATH = "/auth-gate";

	public static final String CALLBACK = "callback";

	public static final String STATE = "state";

	public static final String USER_HINT = "user_hint";

	private static final String CODE_PARAM = "code";

	private static final Logger LOG = LoggerFactory.getLogger(AuthGateServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Validate the loopback callback up front. We don't want to send
		// the user through a login round-trip just to fail on the way back.
		String callback = LoopbackCallbacks.validate(req.getParameter(CALLBACK));
		if (callback == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
					"Ungültige Callback-URL");
			return;
		}
		String state = req.getParameter(STATE);
		if (state == null) state = "";

		String hint = req.getParameter(USER_HINT);
		String user = LoginFilter.getAuthenticatedUser(req);

		if (user != null && hint != null && !hint.isEmpty() && !hint.equals(user)) {
			// Wrong PhoneBlock account in the browser. Send through pac4j's
			// logout filter, redirect back here without the session, so the
			// user gets a clean sign-in form for the right account. The
			// hint stays in the come-back URL so the same check runs again
			// on return — protects against pathological cases where logout
			// somehow doesn't actually clear the session.
			String comeBack = req.getContextPath() + PATH
					+ "?" + CALLBACK  + "=" + urlEncode(callback)
					+ "&" + STATE     + "=" + urlEncode(state)
					+ "&" + USER_HINT + "=" + urlEncode(hint);
			String logoutUrl = req.getContextPath() + "/logout"
					+ "?" + Pac4jConstants.URL + "=" + urlEncode(comeBack);
			LOG.info("auth-gate: session user '{}' ≠ hint '{}' — bouncing through /logout",
					user, hint);
			resp.sendRedirect(logoutUrl);
			return;
		}

		if (user == null) {
			LoginServlet.requestLogin(req, resp);
			return;
		}

		String jwt = IdentityJwt.signAuthGate(user, state);
		String redirectUrl = ServletUtil.withParam(callback, CODE_PARAM, jwt);
		redirectUrl = ServletUtil.withParam(redirectUrl, STATE, state);

		LOG.info("auth-gate: minted JWT for {} → {}", user, callback);
		resp.sendRedirect(redirectUrl);
	}

	private static String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
