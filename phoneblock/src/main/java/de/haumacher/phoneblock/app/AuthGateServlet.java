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
 *        silently logs that session out <em>once</em> and bounces back
 *        to itself, so the user lands on the standard sign-in form
 *        instead of a "wrong account" mint → mismatch → retry → mint
 *        loop.
 * </ul>
 *
 * <p>
 * Behaviour:
 * <ul>
 *   <li>Authenticated, no hint or hint matches → 302 to
 *       {@code <callback>?code=<jwt>&state=<state>}.
 *   <li>Authenticated, hint differs, no prior retry → 302 to
 *       {@code /logout?url=…} pointing back at this servlet with
 *       {@code relogged=1}. After pac4j's session cleanup and the
 *       follow-up login we re-enter here.
 *   <li>Authenticated, hint differs, {@code relogged=1} already set →
 *       mint the JWT anyway and redirect to the callback. The caller
 *       will detect the mismatch and surface a localized error banner
 *       to the user; without this fall-through, a user whose only
 *       PhoneBlock account is the "wrong" one (relative to the hint)
 *       would loop through logout / login / mismatch indefinitely.
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

	/**
	 * Internal flag set on the come-back URL of the silent logout-retry
	 * to break the wrong-account loop after one attempt — see class
	 * Javadoc.
	 */
	public static final String RELOGGED = "relogged";

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
		boolean relogged = "1".equals(req.getParameter(RELOGGED));

		if (user != null && hint != null && !hint.isEmpty() && !hint.equals(user)) {
			if (relogged) {
				// We already cleaned the session once and the user came
				// back logged in as a different account again — most
				// likely they only have this one PhoneBlock account, or
				// they ignored the hint on purpose. Don't loop: mint
				// the JWT and let the caller's mismatch handler render
				// a banner ("this dongle belongs to <hint>, you signed
				// in as <user>"). Falls through to the mint path below.
				LOG.info("auth-gate: session user '{}' ≠ hint '{}' even after relogin — minting anyway",
						user, hint);
			} else {
				// Wrong PhoneBlock account in the browser. Send through
				// pac4j's logout filter, redirect back here without the
				// session, so the user gets a clean sign-in form for the
				// right account. The come-back URL carries `relogged=1`
				// so a second mismatch falls through to JWT mint instead
				// of looping.
				String comeBack = req.getContextPath() + PATH
						+ "?" + CALLBACK  + "=" + urlEncode(callback)
						+ "&" + STATE     + "=" + urlEncode(state)
						+ "&" + USER_HINT + "=" + urlEncode(hint)
						+ "&" + RELOGGED  + "=1";
				String logoutUrl = req.getContextPath() + "/logout"
						+ "?" + Pac4jConstants.URL + "=" + urlEncode(comeBack);
				LOG.info("auth-gate: session user '{}' ≠ hint '{}' — bouncing through /logout",
						user, hint);
				resp.sendRedirect(logoutUrl);
				return;
			}
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
