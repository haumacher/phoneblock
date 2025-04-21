package de.haumacher.phoneblock.app.render.controller;

import java.io.IOException;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class RequireLoginController extends DefaultController {
	
	@Override
	public boolean process(TemplateRenderer renderer, HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		HttpSession httpSession = request.getSession(false);
		String userName = LoginFilter.getAuthenticatedUser(httpSession);
		if (userName == null) {
			LoginServlet.requestLogin(request, response);
			return true;
		}
		
		return super.process(renderer, request, response);
	}

}
