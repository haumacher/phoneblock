package de.haumacher.phoneblock.app.render.controller;

import java.io.IOException;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.settings.AuthToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RequireLoginController extends DefaultController {
	
	@Override
	public boolean process(TemplateRenderer renderer, HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		AuthToken authorization = LoginFilter.getAuthorization(request);
		if (authorization == null || !checkAccessRights(authorization)) {
			LoginServlet.requestLogin(request, response);
			return true;
		}
		
		return super.process(renderer, request, response);
	}

	protected boolean checkAccessRights(AuthToken authorization) {
		return true;
	}

}
