package de.haumacher.phoneblock.app;

import java.io.IOException;

import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Entry point for the "Login with PhoneBlock" SSO flow used by
 * downstream apps (e.g. the dongle web UI) that need to verify a
 * user's PhoneBlock identity without minting a new API token.
 *
 * <p>
 * GET on {@value #PATH} bounces the browser through the existing
 * {@code /mobile/login} form with {@code appId=PhoneBlockAuth} so
 * that {@link CreateAuthTokenServlet#doPost(HttpServletRequest, HttpServletResponse)}
 * branches into the identity-assertion path: instead of calling
 * {@code db.createAPIToken}, it signs a short-lived JWT for the
 * authenticated user and redirects back to the caller's loopback
 * callback URL with {@code ?code=&lt;jwt&gt;&amp;state=&lt;nonce&gt;}.
 *
 * <p>
 * Caller-supplied parameters (forwarded verbatim through the login
 * detour):
 * <ul>
 *   <li>{@code callback} — loopback URL to redirect to on success.
 *   <li>{@code state}    — opaque CSRF nonce echoed back unchanged.
 * </ul>
 */
@WebServlet(urlPatterns = AuthLoginServlet.PATH)
public class AuthLoginServlet extends HttpServlet {

	public static final String PATH = "/auth-login";

	public static final String CALLBACK = "callback";

	public static final String STATE = "state";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String target = req.getContextPath() + CreateAuthTokenServlet.MOBILE_LOGIN;
		target = ServletUtil.withParam(target, CreateAuthTokenServlet.APP_ID,
			CreateAuthTokenServlet.APP_ID_AUTH);
		String callback = req.getParameter(CALLBACK);
		String state    = req.getParameter(STATE);
		if (callback != null) target = ServletUtil.withParam(target, CALLBACK, callback);
		if (state != null)    target = ServletUtil.withParam(target, STATE,    state);
		resp.sendRedirect(target);
	}
}
