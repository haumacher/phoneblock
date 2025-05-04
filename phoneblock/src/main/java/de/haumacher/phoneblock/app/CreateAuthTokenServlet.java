package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.AuthToken;
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
 */
@WebServlet(urlPatterns = CreateAuthTokenServlet.CREATE_TOKEN)
public class CreateAuthTokenServlet extends HttpServlet {

	public static final String CREATE_TOKEN = "/create-token";
	
	private static final String MOBILE_RESPONSE = "/mobile/response";
	
	private static final String TOKEN_PARAM = "token";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.sendRedirect(req.getContextPath() + "/mobile/login.jsp");
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String user = LoginFilter.getAuthenticatedUser(req.getSession(false));
		if (user == null) {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			TemplateRenderer.getInstance(req).process("/mobile/login", req, resp);
			return;
		}

		long now = System.currentTimeMillis();
		DB db = DBService.getInstance();
		AuthToken loginToken = db.createAPIToken(user, now, req.getHeader("User-Agent"));
		
		resp.sendRedirect(req.getContextPath() + MOBILE_RESPONSE + "?" + TOKEN_PARAM + "=" + URLEncoder.encode(loginToken.getToken(), StandardCharsets.UTF_8));
	}
}
