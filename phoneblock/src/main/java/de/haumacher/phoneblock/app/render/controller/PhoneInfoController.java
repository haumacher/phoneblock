package de.haumacher.phoneblock.app.render.controller;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.RatingServlet;
import de.haumacher.phoneblock.app.render.DefaultController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class PhoneInfoController extends DefaultController {
	
	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);
		
		HttpSession session = request.getSession(false);

		request.setAttribute(RatingServlet.ENTERED_RATING_ATTR, session == null ? null : (String) session.getAttribute(RatingServlet.ENTERED_RATING_ATTR));
		request.setAttribute(RatingServlet.ENTERED_COMMENT_ATTR, session == null ? null : (String) session.getAttribute(RatingServlet.ENTERED_COMMENT_ATTR));
		request.setAttribute(RatingServlet.CAPTCHA_ERROR_ATTR,  session == null ? null : (String) session.getAttribute(RatingServlet.CAPTCHA_ERROR_ATTR));
		 
		if (session != null) {
			session.removeAttribute(RatingServlet.CAPTCHA_ERROR_ATTR);
			session.removeAttribute(RatingServlet.ENTERED_RATING_ATTR);
			session.removeAttribute(RatingServlet.ENTERED_COMMENT_ATTR);
		}		
		
		LoginController.addCaptcha(ctx, request);
	}
}
