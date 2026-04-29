package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.util.IdentityJwt;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Public verifier for assertions produced by {@link AuthGateServlet}.
 *
 * <p>
 * Validates signature, expiry and optional CSRF nonce of a JWT, and
 * returns the subject so the caller can compare it against the
 * identity it has on file (e.g. the dongle's stored "owner" name).
 * Intentionally <b>not</b> bearer-authenticated: tying the auth gate
 * to an API token would brick a device whose token the user later
 * deletes on phoneblock.net. The dongle's lockout protection now
 * lives entirely on the client side — it pins the owner at first
 * activation and refuses any subsequent JWT whose subject does not
 * match.
 *
 * <p>
 * <b>POST /auth/verify-code</b><br>
 * Body (form-encoded):
 * <ul>
 *   <li>{@code code}  — JWT received from {@code /auth/gate}'s callback redirect.
 *   <li>{@code state} — nonce the caller sent to {@code /auth/gate} (optional).
 * </ul>
 *
 * <p>Always returns HTTP 200 with a JSON body:
 * <ul>
 *   <li>{@code {"ok":true,"user":"alice@example.com"}} on success
 *   <li>{@code {"ok":false,"reason":"invalid_token"}} otherwise — reason is informational.
 * </ul>
 */
@WebServlet(urlPatterns = AuthVerifyServlet.PATH)
public class AuthVerifyServlet extends HttpServlet {

	public static final String PATH = "/auth/verify-code";

	private static final Logger LOG = LoggerFactory.getLogger(AuthVerifyServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String code  = req.getParameter("code");
		String state = req.getParameter("state");

		IdentityJwt.Claims claims;
		try {
			claims = IdentityJwt.verify(code, IdentityJwt.PURPOSE_AUTH_GATE);
		} catch (IdentityJwt.InvalidTokenException e) {
			LOG.info("verify-code: rejected: {}", e.getMessage());
			writeResult(resp, false, null, "invalid_token");
			return;
		}

		// Bind the assertion to the caller's CSRF nonce: an attacker
		// who somehow obtained a valid JWT for a different login
		// attempt cannot replay it through a different device.
		if (state != null && !state.equals(claims.nonce)) {
			LOG.info("verify-code: nonce mismatch (jwt-sub={})", claims.sub);
			writeResult(resp, false, null, "nonce_mismatch");
			return;
		}

		LOG.info("verify-code: accepted for {}", claims.sub);
		writeResult(resp, true, claims.sub, null);
	}

	private static void writeResult(HttpServletResponse resp, boolean ok,
			String user, String reason) throws IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		try (PrintWriter pw = resp.getWriter();
		     JsonWriter w = new JsonWriter(new WriterAdapter(pw))) {
			w.beginObject();
			w.name("ok"); w.value(ok);
			if (user != null) {
				w.name("user"); w.value(user);
			}
			if (reason != null) {
				w.name("reason"); w.value(reason);
			}
			w.endObject();
		}
	}
}
