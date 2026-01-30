package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.sendRedirect(req.getContextPath() + MOBILE_LOGIN);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String user = LoginFilter.getAuthenticatedUser(req.getSession(false));
		if (user == null) {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			TemplateRenderer.getInstance(req).process(MOBILE_LOGIN, req, resp);
			return;
		}

		long now = System.currentTimeMillis();
		DB db = DBService.getInstance();
		String label = req.getParameter(TOKEN_LABEL);
		AuthToken loginToken = db.createAPIToken(user, now, req.getHeader("User-Agent"), label);

		String appId = req.getParameter(APP_ID);
		if (appId == null) {
			appId = "PhoneBlockMobile";
		}
		
		// TODO: This might better come from a DB table (registered integrations):
		String baseUrl = switch (appId) {
			case "PhoneSpamBlocker" -> "PhoneSpamBlocker://auth";
			default -> req.getContextPath() + MOBILE_RESPONSE;
		};
		
		resp.sendRedirect(ServletUtil.withParam(baseUrl, TOKEN_PARAM, loginToken.getToken()));
	}
}
