package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.util.IdentityJwt;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Verifies an identity assertion produced by the
 * {@code /auth-login} SSO flow against the bearer token of the
 * caller. Returns {@code {"ok": true}} only if the JWT is valid
 * (signature, expiry, optional state nonce) AND its subject
 * equals the user the bearer token belongs to. The combination
 * proves to the caller (e.g. the dongle) that the human at the
 * browser is the same PhoneBlock user whose API token the device
 * holds.
 *
 * <p>
 * <b>POST /api/verify-auth-code</b><br>
 * Authentication: {@code Authorization: Bearer &lt;api-token&gt;}<br>
 * Body (form-encoded):
 * <ul>
 *   <li>{@code code}  — the JWT received from {@code /auth-login}'s callback redirect.
 *   <li>{@code state} — the nonce the caller sent to {@code /auth-login} (optional).
 * </ul>
 *
 * <p>Always returns HTTP 200 with a JSON body. The {@code ok}
 * flag is the authoritative answer; the {@code reason} field is
 * informational and may be logged but should not be displayed
 * verbatim to end users.
 */
@WebServlet(urlPatterns = VerifyAuthCodeServlet.PATH)
public class VerifyAuthCodeServlet extends HttpServlet {

	public static final String PATH = "/api/verify-auth-code";

	private static final Logger LOG = LoggerFactory.getLogger(VerifyAuthCodeServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String tokenUser = LoginFilter.getAuthenticatedUser(req);
		if (tokenUser == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		String code  = req.getParameter("code");
		String state = req.getParameter("state");

		IdentityJwt.Claims claims;
		try {
			claims = IdentityJwt.verify(code);
		} catch (IdentityJwt.InvalidTokenException e) {
			LOG.info("verify-auth-code: rejected for {}: {}", tokenUser, e.getMessage());
			writeResult(resp, false, "invalid_token");
			return;
		}

		// Bind the assertion to the caller's CSRF nonce: an attacker
		// who somehow obtained a valid JWT for a different login
		// attempt cannot replay it through a different device.
		if (state != null && !state.equals(claims.nonce)) {
			LOG.info("verify-auth-code: nonce mismatch for {}", tokenUser);
			writeResult(resp, false, "nonce_mismatch");
			return;
		}

		if (!tokenUser.equals(claims.sub)) {
			LOG.info("verify-auth-code: user mismatch — token={} jwt-sub={}",
				tokenUser, claims.sub);
			writeResult(resp, false, "user_mismatch");
			return;
		}

		LOG.info("verify-auth-code: accepted for {}", tokenUser);
		writeResult(resp, true, null);
	}

	private static void writeResult(HttpServletResponse resp, boolean ok, String reason) throws IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		StringBuilder body = new StringBuilder();
		body.append("{\"ok\":").append(ok);
		if (reason != null) {
			body.append(",\"reason\":\"").append(reason).append('"');
		}
		body.append('}');
		try (PrintWriter w = resp.getWriter()) {
			w.write(body.toString());
		}
	}
}
