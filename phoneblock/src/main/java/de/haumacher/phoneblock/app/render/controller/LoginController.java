package de.haumacher.phoneblock.app.render.controller;

import java.io.IOException;
import java.io.Writer;

import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.IServletWebExchange;

import de.haumacher.phoneblock.app.EMailVerificationServlet;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.oauth.PhoneBlockConfigFactory;
import de.haumacher.phoneblock.app.render.DefaultController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginController extends DefaultController {
	
	@Override
	public void process(IServletWebExchange webExchange, ITemplateEngine templateEngine, Writer writer) throws IOException {
		// If already logged in, forward to final destination.
		HttpServletRequest request = (HttpServletRequest) webExchange.getNativeRequestObject();
		String userName = LoginFilter.getAuthenticatedUser(request);
		if (userName != null) {
			LoginServlet.redirectToLocationAfterLogin(request, (HttpServletResponse) webExchange.getNativeResponseObject());
			return;
		}
		
		super.process(webExchange, templateEngine, writer);
	}
	
	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);
		
		ctx.setVariable("googleClient", PhoneBlockConfigFactory.GOOGLE_CLIENT);
		ctx.setVariable("loginWeb", EMailVerificationServlet.LOGIN_WEB);
		ctx.setVariable("loginMobile", EMailVerificationServlet.LOGIN_MOBILE);
		ctx.setVariable("loginAction", LoginServlet.PATH);
		ctx.setVariable("rememberParam", LoginServlet.REMEMBER_ME_PARAM);
		ctx.setVariable("userNameParam", LoginServlet.USER_NAME_PARAM);
		ctx.setVariable("passwordParam", LoginServlet.PASSWORD_PARAM);
	}

}
