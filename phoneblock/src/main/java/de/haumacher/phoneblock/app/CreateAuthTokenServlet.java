package de.haumacher.phoneblock.app;

import java.io.IOException;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.AuthToken;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet for requesting a fresh authorization token to use for API access.
 */
@WebServlet(urlPatterns = CreateAuthTokenServlet.CREATE_TOKEN)
public class CreateAuthTokenServlet extends HttpServlet {

	public static final String CREATE_TOKEN = "/create-token";
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.sendRedirect(req.getContextPath() + "/mobile/login.jsp");
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String user = LoginFilter.getAuthenticatedUser(req.getSession(false));
		if (user == null) {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			req.getRequestDispatcher("/mobile/login.jsp").forward(req, resp);
			return;
		}

		long now = System.currentTimeMillis();
		DB db = DBService.getInstance();
		AuthToken loginToken = db.createLoginToken(user, now, req.getHeader("User-Agent"));
		
		req.setAttribute("token", loginToken.getToken());
		req.getRequestDispatcher("/mobile/response.jsp").forward(req, resp);
	}
}
