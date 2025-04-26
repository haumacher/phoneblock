package de.haumacher.phoneblock.app;

import java.io.IOException;

import de.haumacher.phoneblock.app.render.TemplateRenderer;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet rendering error pages.
 */
@WebServlet(urlPatterns = {
	ErrorServlet.NOT_FOUND_PATH,
	ErrorServlet.NOT_ALLOWED_PATH,
})
public class ErrorServlet extends HttpServlet {
	
	public static final String NOT_FOUND_PATH = "/error-not-found";
	public static final String NOT_ALLOWED_PATH = "/error-not-allowed";
	public static final String NOT_AUTHENTICATED_PATH = "/error-not-authenticated";
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		TemplateRenderer.getInstance(req).process(req.getServletPath(), req, resp);
	}

}
