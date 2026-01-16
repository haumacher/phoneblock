package de.haumacher.phoneblock.app.render.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.CreateAuthTokenServlet;
import jakarta.servlet.http.HttpServletRequest;

public class MobileLoginController extends AbstractLoginController {

	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);

		// Preserve token label for mobile token creation
		String tokenLabel = request.getParameter(CreateAuthTokenServlet.TOKEN_LABEL);
		if (tokenLabel != null && !tokenLabel.trim().isEmpty()) {
			ctx.setVariable(CreateAuthTokenServlet.TOKEN_LABEL, tokenLabel);
			ctx.setVariable("location", "/mobile/login?" + CreateAuthTokenServlet.TOKEN_LABEL + "=" + URLEncoder.encode(tokenLabel, StandardCharsets.UTF_8));
		} else {
			ctx.setVariable(CreateAuthTokenServlet.TOKEN_LABEL, "");
			ctx.setVariable("location", "/mobile/login");
		}
	}
	
}
