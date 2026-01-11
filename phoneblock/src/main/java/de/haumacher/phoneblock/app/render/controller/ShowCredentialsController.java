package de.haumacher.phoneblock.app.render.controller;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.RegistrationServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Controller for displaying newly created user credentials.
 */
public class ShowCredentialsController extends RequireLoginController {

	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);

		HttpSession session = request.getSession(false);
		if (session != null) {
			String passwd = RegistrationServlet.getPassword(session);
			if (passwd != null) {
				request.setAttribute("passwd", passwd);
				// Remove password from session after displaying it (show only once)
				session.removeAttribute("passwd");
			}
		}

		// Get the location parameter to redirect after showing credentials
		String location = LoginServlet.location(request);
		request.setAttribute("location", location);
	}
}
