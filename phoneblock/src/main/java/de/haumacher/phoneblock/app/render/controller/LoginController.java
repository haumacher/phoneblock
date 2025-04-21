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

public class LoginController extends DefaultController {
	
	@Override
	public boolean process(TemplateRenderer renderer, HttpServletRequest request, HttpServletResponse response) throws IOException {
		// If already logged in, forward to final destination.
		String userName = LoginFilter.getAuthenticatedUser(request);
		if (userName != null) {
			LoginServlet.redirectToLocationAfterLogin(request, response);
			return true;
		}

		return super.process(renderer, request, response);
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
		
		addCaptcha(ctx, request);
	}

	public static void addCaptcha(WebContext ctx, HttpServletRequest request) {
		Captcha captcha = new Captcha(SecureRandomService.getInstance().getRnd());
		request.getSession().setAttribute("captcha", captcha.getText());
		
		ctx.setVariable("captchaSrc", "data:image/png;base64, " + Base64.getEncoder().encodeToString(captcha.getPng()));
	}

}
