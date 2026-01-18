/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.MailServiceStarter;
import de.haumacher.phoneblock.util.I18N;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import static de.haumacher.phoneblock.app.CreateAuthTokenServlet.APP_ID;
import static de.haumacher.phoneblock.app.CreateAuthTokenServlet.TOKEN_LABEL;

/**
 * {@link HttpServlet} that is invoked from the <code>login.jsp</code> form when requesting to login by e-mail.
 */
@WebServlet(urlPatterns = {
	EMailVerificationServlet.LOGIN_WEB,
	EMailVerificationServlet.LOGIN_MOBILE,
})
public class EMailVerificationServlet extends HttpServlet {

	/**
	 * Request attribute holding the page to re-start login/signup.
	 */
	public static final String RESTART_PAGE_ATTR = "restartPage";

	/**
	 * Request attribute set, if e-mail verification failed.
	 */
	public static final String EMAIL_MESSAGE_ATTR = "emailMessage";
	
	/**
	 * Request attribute set, if captcha verification failed.
	 */
	public static final String CAPTCHA_MESSAGE_ATTR = "captchaMessage";
	
	public static final String LOGIN_WEB = "/login-web";
	
	public static final String LOGIN_MOBILE = "/login-mobile";
	
	
	private static final Logger LOG = LoggerFactory.getLogger(EMailVerificationServlet.class);
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String redirectUrl = req.getContextPath() + failurePage(req);

		resp.sendRedirect(forwardTokenParams(redirectUrl, req));
	}

	public static String forwardTokenParams(String redirectUrl, HttpServletRequest req) {
		return ServletUtil.forwardParam(redirectUrl, req, APP_ID, TOKEN_LABEL);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		LoginServlet.forwardLocation(req);

		String email = req.getParameter("email");
		if (email == null || email.trim().isEmpty()) {
			sendEmailFailure(req, resp, I18N.getMessage(getUserLocale(req), "error.email.verification.empty"));
			return;
		}

		req.setAttribute("email", email);

		String captcha = req.getParameter("captcha");
		if (captcha == null || captcha.trim().isEmpty()) {
			sendCaptchaFailure(req, resp, I18N.getMessage(getUserLocale(req), "error.email.verification.captcha-empty"));
			return;
		}

		HttpSession session = req.getSession();
		String captchaExpected = (String) session.getAttribute("captcha");
		session.removeAttribute("captcha");
		if (!captcha.trim().equals(captchaExpected)) {
			sendCaptchaFailure(req, resp, I18N.getMessage(getUserLocale(req), "error.email.verification.captcha-mismatch"));
			return;
		}
		
    	try {
			InternetAddress address = new InternetAddress(email.trim());
			
			String plainAddress = address.getAddress();
			int atIndex = plainAddress.indexOf('@');
			if (atIndex <= 0) {
				req.removeAttribute("email");
				sendEmailFailure(req, resp, I18N.getMessage(getUserLocale(req), "error.email.verification.no-username"));
				return;
			}
			String domain = plainAddress.substring(atIndex + 1);
			Record[] result = new Lookup(domain, Type.MX, DClass.IN).run();
			if (result == null || result.length == 0) {
				req.removeAttribute("email");
				sendEmailFailure(req, resp, I18N.getMessage(getUserLocale(req), "error.email.verification.invalid-domain", domain));
				return;
			}

			MXRecord mx = (MXRecord) result[0];
			String mxHost = mx.getTarget().toString(true);
			if (".".equals(mxHost)) {
				req.removeAttribute("email");
				sendEmailFailure(req, resp, I18N.getMessage(getUserLocale(req), "error.email.verification.domain-no-email", domain));
				return;
			}
		} catch (AddressException ex) {
			req.removeAttribute("email");
			sendEmailFailure(req, resp, I18N.getMessage(getUserLocale(req), "error.email.verification.invalid", ex.getMessage()));
			return;
		}
    	
		String code = DBService.getInstance().generateVerificationCode();
		
		// End verification code to email address.
		try {
			MailService mailService = MailServiceStarter.getInstance();
			if (mailService == null) {
				LOG.error("Mail service not active!");
				sendEmailFailure(req, resp, I18N.getMessage(getUserLocale(req), "error.email.verification.service-unavailable"));
				return;
			}

			mailService.sendActivationMail(email, code);
		} catch (AddressException ex) {
			LOG.warn("Failed to send message: " + ex.getMessage());
			sendEmailFailure(req, resp, I18N.getMessage(getUserLocale(req), "error.email.verification.send-failed", ex.getMessage()));
			return;
		} catch (Exception ex) {
			LOG.error("Failed to send message.", ex);
			sendEmailFailure(req, resp, I18N.getMessage(getUserLocale(req), "error.email.verification.send-failed", ex.getMessage()));
			return;
		}
		
		session.setAttribute("email", email);
		session.setAttribute("code", code);

		req.setAttribute(RESTART_PAGE_ATTR, failurePage(req));
		preserveTokenParamsForRendering(req);
		TemplateRenderer.getInstance(req).process(successPage(req), req, resp);
	}

	private void sendEmailFailure(HttpServletRequest req, HttpServletResponse resp, String message)
			throws ServletException, IOException {
		req.setAttribute(EMAIL_MESSAGE_ATTR, message);
		preserveTokenParamsForRendering(req);
		TemplateRenderer.getInstance(req).process(failurePage(req), req, resp);
	}

	private void sendCaptchaFailure(HttpServletRequest req, HttpServletResponse resp, String message)
			throws ServletException, IOException {
		req.setAttribute(CAPTCHA_MESSAGE_ATTR, message);
		preserveTokenParamsForRendering(req);
		TemplateRenderer.getInstance(req).process(failurePage(req), req, resp);
	}

	/**
	 * Ensures token label is available for template rendering.
	 */
	private void preserveTokenParamsForRendering(HttpServletRequest req) {
		// Token label might come from form submission, ensure it's in request parameters
		// for MobileLoginController to pick up
		ServletUtil.declareAttribute(req, APP_ID);
		ServletUtil.declareAttribute(req, TOKEN_LABEL);
	}

	/**
	 * The page to redirect, if something went wrong.
	 */
	private static String failurePage(HttpServletRequest req) {
		switch (req.getServletPath()) {
		case LOGIN_MOBILE:
			return "/mobile/login";
		case LOGIN_WEB:
		default:
			return "/login";
		}
	}

	private String successPage(HttpServletRequest req) {
		switch (req.getServletPath()) {
			case LOGIN_MOBILE:
				return "/mobile/code";
			case LOGIN_WEB:
			default:
				return "/signup-code";
		}
	}

	private String getUserLocale(HttpServletRequest req) {
		// For login/signup flows, user is not yet authenticated, so fallback to browser locale
		return req.getLocale().toLanguageTag();
	}

}
