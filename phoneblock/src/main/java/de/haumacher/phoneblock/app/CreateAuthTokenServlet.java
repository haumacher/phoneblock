package de.haumacher.phoneblock.app;

import java.io.IOException;

import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.util.IdentityJwt;
import de.haumacher.phoneblock.util.LoopbackCallbacks;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet for requesting a fresh authorization token to use for API access from a mobile application.
 *
 * <p>
 * Upon success, control flow is redirected to {@value #MOBILE_RESPONSE} with the token passed as
 * {@value #TOKEN_PARAM} parameter. This URL should be redirected to the native app running on the
 * mobile device.
 * </p>
 *
 * <p>
 * An optional {@value #TOKEN_LABEL} parameter can be provided to give the token a user-visible label that will
 * be displayed in the settings pages.
 * </p>
 */
@WebServlet(urlPatterns = CreateAuthTokenServlet.CREATE_TOKEN)
public class CreateAuthTokenServlet extends HttpServlet {

	public static final String CREATE_TOKEN = "/create-token";

	public static final String MOBILE_LOGIN = "/mobile/login";

	private static final String MOBILE_RESPONSE = "/mobile/response";

	private static final String TOKEN_PARAM = "loginToken";

	/**
	 * Request/session parameter for device label in mobile token creation flow.
	 */
	public static final String TOKEN_LABEL = "tokenLabel";

	/**
	 * Request/session parameter for identifying the app for which a token is issued.
	 */
	public static final String APP_ID = "appId";

	/**
	 * Request/session parameter for a dynamic callback URL — used by the
	 * PhoneBlock dongle, where the callback points back to the dongle's own
	 * LAN-local web UI (e.g. {@code http://answerbot/token-callback}).
	 */
	public static final String CALLBACK = "callback";

	/**
	 * CSRF nonce passed through the login round-trip so the caller can
	 * verify the redirect it receives corresponds to its own request.
	 */
	public static final String STATE = "state";

	/**
	 * App-ID marking a request from a dongle; enables the {@link #CALLBACK}
	 * parameter path.
	 */
	public static final String APP_ID_DONGLE = "PhoneBlockDongle";

	/**
	 * App-ID for the "Login with PhoneBlock" SSO flow. Switches the
	 * POST handler from minting a new API token to signing a short-
	 * lived identity JWT that the caller verifies via
	 * {@code /api/verify-auth-code}.
	 */
	public static final String APP_ID_AUTH = "PhoneBlockAuth";

	private static final String CODE_PARAM = "code";
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.sendRedirect(req.getContextPath() + MOBILE_LOGIN);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String user = LoginFilter.getAuthenticatedUser(req);
		if (user == null) {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			TemplateRenderer.getInstance(req).process(MOBILE_LOGIN, req, resp);
			return;
		}

		String appId = req.getParameter(APP_ID);
		if (appId == null) {
			appId = "PhoneBlockMobile";
		}

		// Validate the loopback callback parameter BEFORE creating
		// the credential — we don't want to hand out a token / JWT
		// if we'd have to refuse to deliver it afterwards.
		String callback = null;
		if (APP_ID_DONGLE.equals(appId) || APP_ID_AUTH.equals(appId)) {
			callback = LoopbackCallbacks.validate(req.getParameter(CALLBACK));
			if (callback == null) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
					"Ungültige Callback-URL");
				return;
			}
		}

		String redirectUrl;
		// TODO: This might better come from a DB table (registered integrations):
		switch (appId) {
			case APP_ID_AUTH: {
				// SSO flow: do NOT mint an API token. Sign a short-
				// lived JWT that the caller verifies via
				// /api/verify-auth-code with its existing bearer token.
				String state = req.getParameter(STATE);
				String jwt = IdentityJwt.sign(user, state == null ? "" : state);
				redirectUrl = ServletUtil.withParam(callback, CODE_PARAM, jwt);
				if (state != null) {
					redirectUrl = ServletUtil.withParam(redirectUrl, STATE, state);
				}
				break;
			}
			case "PhoneSpamBlocker":
			case APP_ID_DONGLE:
			default: {
				long now = System.currentTimeMillis();
				DB db = DBService.getInstance();
				String label = req.getParameter(TOKEN_LABEL);
				AuthToken loginToken = db.createAPIToken(user, now, req.getHeader("User-Agent"), label);
				switch (appId) {
					case "PhoneSpamBlocker":
						redirectUrl = ServletUtil.withParam("PhoneSpamBlocker://auth",
							TOKEN_PARAM, loginToken.getToken());
						break;
					case APP_ID_DONGLE:
						redirectUrl = ServletUtil.withParam(callback,
							TOKEN_PARAM, loginToken.getToken());
						redirectUrl = ServletUtil.withParam(redirectUrl,
							STATE, req.getParameter(STATE));
						break;
					default:
						redirectUrl = ServletUtil.withParam(
							req.getContextPath() + MOBILE_RESPONSE,
							TOKEN_PARAM, loginToken.getToken());
						break;
				}
				break;
			}
		}

		resp.sendRedirect(redirectUrl);
	}

}
