package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.AuthToken;
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

		// Validate the dongle-specific callback parameter BEFORE creating
		// the token — we don't want to hand out credentials if we'd have
		// to refuse to deliver them afterwards.
		String callback = null;
		if (APP_ID_DONGLE.equals(appId)) {
			callback = validateDongleCallback(req.getParameter(CALLBACK));
			if (callback == null) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
					"Ungültige Callback-URL");
				return;
			}
		}

		long now = System.currentTimeMillis();
		DB db = DBService.getInstance();
		String label = req.getParameter(TOKEN_LABEL);
		AuthToken loginToken = db.createAPIToken(user, now, req.getHeader("User-Agent"), label);

		String redirectUrl;
		// TODO: This might better come from a DB table (registered integrations):
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

		resp.sendRedirect(redirectUrl);
	}

	/**
	 * Reject any callback URL that could leak a bearer token outside the
	 * user's LAN. Returns the (possibly normalized) URL on success,
	 * {@code null} if it should be refused.
	 *
	 * <p>
	 * Accepted: plain {@code http://} scheme, and a host that is either a
	 * private-range IPv4 address, a hostname ending in {@code .fritz.box}
	 * or {@code .local}, or the bare literal {@code answerbot}/{@code localhost}.
	 * Everything else — public hostnames, odd schemes, userinfo, fragments —
	 * is refused.
	 */
	static String validateDongleCallback(String raw) {
		if (raw == null || raw.isBlank()) return null;
		URI uri;
		try {
			uri = new URI(raw);
		} catch (URISyntaxException e) {
			return null;
		}
		if (!"http".equalsIgnoreCase(uri.getScheme())) return null;
		if (uri.getUserInfo() != null)  return null;
		if (uri.getFragment() != null)  return null;
		String host = uri.getHost();
		if (host == null) return null;
		String h = host.toLowerCase();
		boolean hostOk =
			h.equals("localhost") ||
			h.equals("answerbot") ||
			h.endsWith(".fritz.box") ||
			h.endsWith(".local") ||
			isPrivateIp(h);
		if (!hostOk) return null;
		return uri.toString();
	}

	private static boolean isPrivateIp(String host) {
		String[] parts = host.split("\\.");
		if (parts.length != 4) return false;
		int[] o = new int[4];
		for (int i = 0; i < 4; i++) {
			try {
				o[i] = Integer.parseInt(parts[i]);
			} catch (NumberFormatException e) {
				return false;
			}
			if (o[i] < 0 || o[i] > 255) return false;
		}
		if (o[0] == 10) return true;
		if (o[0] == 127) return true;
		if (o[0] == 169 && o[1] == 254) return true;
		if (o[0] == 172 && o[1] >= 16 && o[1] <= 31) return true;
		if (o[0] == 192 && o[1] == 168) return true;
		return false;
	}
}
