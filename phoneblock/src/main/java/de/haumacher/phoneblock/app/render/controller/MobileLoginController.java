package de.haumacher.phoneblock.app.render.controller;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.CreateAuthTokenServlet;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;

public class MobileLoginController extends AbstractLoginController {

	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);

		// Preserve token label for mobile token creation
		declareVariable(ctx, request, CreateAuthTokenServlet.APP_ID);
		declareVariable(ctx, request, CreateAuthTokenServlet.TOKEN_LABEL);

		// Use existing location if available (e.g., when returning from failed captcha),
		// otherwise build from appId/tokenLabel parameters (initial page load)
		String existingLocation = LoginServlet.location(request, null);
		if (existingLocation != null) {
			ctx.setVariable("location", existingLocation);
		} else {
			ctx.setVariable("location", ServletUtil.forwardParam(CreateAuthTokenServlet.MOBILE_LOGIN, request, CreateAuthTokenServlet.APP_ID, CreateAuthTokenServlet.TOKEN_LABEL));
		}
	}

	private void declareVariable(WebContext ctx, HttpServletRequest request, String fromParam) {
		String tokenLabel = request.getParameter(fromParam);
		if (tokenLabel != null) {
			ctx.setVariable(fromParam, tokenLabel);
		} else {
			ctx.setVariable(fromParam, "");
		}
	}
	
}
