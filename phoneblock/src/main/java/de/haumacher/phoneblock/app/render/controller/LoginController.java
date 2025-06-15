package de.haumacher.phoneblock.app.render.controller;

import java.io.IOException;
import java.util.Base64;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.EMailVerificationServlet;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.oauth.PhoneBlockConfigFactory;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.captcha.Captcha;
import de.haumacher.phoneblock.random.SecureRandomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginController extends MobileLoginController {
	
	@Override
	public boolean process(TemplateRenderer renderer, HttpServletRequest request, HttpServletResponse response) throws IOException {
		// If already logged in, redirect to final destination.
		String userName = LoginFilter.getAuthenticatedUser(request);
		if (userName != null) {
			LoginServlet.redirectToLocationAfterLogin(request, response);
			return true;
		}

		return super.process(renderer, request, response);
	}
	
}
