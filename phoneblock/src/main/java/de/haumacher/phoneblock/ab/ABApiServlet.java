package de.haumacher.phoneblock.ab;

import java.io.IOException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Base class for implementing the answerbot control API.
 */
public abstract class ABApiServlet extends HttpServlet {

	protected void sendError(HttpServletResponse resp, int code, String message) throws IOException {
		resp.setContentType("text/plain");
		resp.setCharacterEncoding("utf-8");
		resp.setStatus(code);
		resp.getWriter().append(message);
	}

}
