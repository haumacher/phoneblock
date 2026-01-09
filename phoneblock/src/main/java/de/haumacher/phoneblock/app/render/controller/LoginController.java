package de.haumacher.phoneblock.app.render.controller;

import java.io.IOException;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginController extends MobileLoginController {

	private String _defaultLocation;

	public LoginController(String defaultLocation) {
		_defaultLocation = defaultLocation;
	}

	@Override
	public boolean process(TemplateRenderer renderer, HttpServletRequest request, HttpServletResponse response) throws IOException {
		// If already logged in, redirect to final destination.
		String userName = LoginFilter.getAuthenticatedUser(request);
		if (userName != null) {
			LoginServlet.redirectToLocationAfterLogin(request, response, _defaultLocation);
			return true;
		}

		return super.process(renderer, request, response);
	}

	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);

		// Override location for regular login (not mobile login)
		// Use location parameter from request, or default to settings page
		String location = request.getParameter(LoginServlet.LOCATION_ATTRIBUTE);
		if (location == null || location.trim().isEmpty()) {
			location = _defaultLocation;
		}
		ctx.setVariable(LoginServlet.LOCATION_ATTRIBUTE, location);
	}

}
