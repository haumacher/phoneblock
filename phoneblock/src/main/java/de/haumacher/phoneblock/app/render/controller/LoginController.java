package de.haumacher.phoneblock.app.render.controller;

import java.io.IOException;

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
	
}
