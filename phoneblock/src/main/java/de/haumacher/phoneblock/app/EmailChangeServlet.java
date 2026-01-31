/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.MailServiceStarter;
import de.haumacher.phoneblock.util.I18N;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Servlet for handling email address changes with verification.
 */
@WebServlet(urlPatterns = EmailChangeServlet.PATH)
public class EmailChangeServlet extends HttpServlet {

	private static final String CHANGE_PAGE = "/change-email";

	private static final String VERIFY_PAGE = "/change-email-verify";

	public static final String PATH = CHANGE_PAGE;

	private static final String ERROR_MESSAGE_ATTR = "errorMessage";
	private static final String NEW_EMAIL_ATTR = "newEmail";
	private static final String EMAIL_ATTR = "email";

	// Session attributes
	private static final String SESSION_NEW_EMAIL = "emailChangeNewEmail";
	private static final String SESSION_CODE = "emailChangeCode";
	private static final String SESSION_TIME = "emailChangeTime";
	private static final String SESSION_ATTEMPTS = "emailChangeAttempts";
	private static final String SESSION_REQUESTS = "emailChangeRequests";

	// Limits
	private static final int MAX_ATTEMPTS = 5;
	private static final int MAX_REQUESTS_PER_SESSION = 3;
	private static final long CODE_EXPIRATION_MS = 30 * 60 * 1000; // 30 minutes

	private static final Logger LOG = LoggerFactory.getLogger(EmailChangeServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			resp.sendRedirect(req.getContextPath() + "/login");
			return;
		}

		// Check if we have a pending email change in session
		HttpSession session = req.getSession();
		String pendingEmail = (String) session.getAttribute(SESSION_NEW_EMAIL);

		if (pendingEmail != null) {
			// Show code entry form
			req.setAttribute(NEW_EMAIL_ATTR, pendingEmail);
			TemplateRenderer.getInstance(req).process(VERIFY_PAGE, req, resp);
		} else {
			// Show email entry form
			TemplateRenderer.getInstance(req).process(CHANGE_PAGE, req, resp);
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			resp.sendRedirect(req.getContextPath() + "/login");
			return;
		}

		String action = req.getParameter("action");
		if ("request".equals(action)) {
			handleEmailChangeRequest(req, resp, userName);
		} else if ("verify".equals(action)) {
			handleCodeVerification(req, resp, userName);
		} else {
			resp.sendRedirect(req.getContextPath() + SettingsServlet.PATH);
		}
	}

	private void handleEmailChangeRequest(HttpServletRequest req, HttpServletResponse resp, String userName)
			throws ServletException, IOException {
		HttpSession session = req.getSession();

		// Check request rate limit
		Integer requests = (Integer) session.getAttribute(SESSION_REQUESTS);
		if (requests != null && requests >= MAX_REQUESTS_PER_SESSION) {
			showError(req, resp, CHANGE_PAGE, "error.email.change.too-many-requests");
			return;
		}

		String newEmail = req.getParameter("newEmail");
		if (newEmail == null || newEmail.trim().isEmpty()) {
			req.setAttribute(EMAIL_ATTR, newEmail);
			showError(req, resp, CHANGE_PAGE, "error.email.change.empty");
			return;
		}

		newEmail = newEmail.trim();
		req.setAttribute(EMAIL_ATTR, newEmail);

		// Get current email to prevent setting the same email
		UserSettings settings = LoginFilter.getUserSettings(req);
		if (settings != null && newEmail.equalsIgnoreCase(settings.getEmail())) {
			showError(req, resp, CHANGE_PAGE, "error.email.change.same-as-current");
			return;
		}

		// Validate email format and MX records
		try {
			InternetAddress address = new InternetAddress(newEmail);

			String plainAddress = address.getAddress();
			int atIndex = plainAddress.indexOf('@');
			if (atIndex <= 0) {
				showError(req, resp, CHANGE_PAGE, "error.email.change.no-username");
				return;
			}

			String domain = plainAddress.substring(atIndex + 1);
			Record[] result = new Lookup(domain, Type.MX, DClass.IN).run();
			if (result == null || result.length == 0) {
				showError(req, resp, CHANGE_PAGE, "error.email.change.invalid-domain", domain);
				return;
			}

			MXRecord mx = (MXRecord) result[0];
			String mxHost = mx.getTarget().toString(true);
			if (".".equals(mxHost)) {
				showError(req, resp, CHANGE_PAGE, "error.email.change.domain-no-email", domain);
				return;
			}
		} catch (AddressException ex) {
			showError(req, resp, CHANGE_PAGE, "error.email.change.invalid", ex.getMessage());
			return;
		}

		// Check if email is already in use by another user
		int emailUseCount;
		DB db = DBService.getInstance();
		try (SqlSession sqlSession = db.openSession()) {
			Users users = sqlSession.getMapper(Users.class);
			emailUseCount = users.isEmailInUse(newEmail);
		}
		if (emailUseCount > 0) {
			showError(req, resp, CHANGE_PAGE, "error.email.change.already-in-use");
			return;
		}

		// Generate verification code
		String code = DBService.getInstance().generateVerificationCode();

		// Send verification email to NEW address
		try {
			MailService mailService = MailServiceStarter.getInstance();
			if (mailService == null) {
				LOG.error("Mail service not active!");
				showError(req, resp, CHANGE_PAGE, "error.email.change.service-unavailable");
				return;
			}

			// Get locale from request (authenticated user's preference or browser locale)
			String locale = I18N.getUserLocale(req);
			mailService.sendEmailChangeMail(newEmail, code, locale);
		} catch (AddressException ex) {
			LOG.warn("Failed to send message: " + ex.getMessage());
			showError(req, resp, CHANGE_PAGE, "error.email.change.send-failed", ex.getMessage());
			return;
		} catch (Exception ex) {
			LOG.error("Failed to send message.", ex);
			showError(req, resp, CHANGE_PAGE, "error.email.change.send-failed", ex.getMessage());
			return;
		}

		// Store in session
		session.setAttribute(SESSION_NEW_EMAIL, newEmail);
		session.setAttribute(SESSION_CODE, code);
		session.setAttribute(SESSION_TIME, System.currentTimeMillis());
		session.setAttribute(SESSION_ATTEMPTS, 0);

		// Increment request counter
		session.setAttribute(SESSION_REQUESTS, requests == null ? 1 : requests + 1);

		LOG.info("Email change verification code sent to '{}' for user '{}'.", newEmail, userName);

		// Show code entry form
		req.setAttribute(NEW_EMAIL_ATTR, newEmail);
		TemplateRenderer.getInstance(req).process(VERIFY_PAGE, req, resp);
	}

	private void handleCodeVerification(HttpServletRequest req, HttpServletResponse resp, String userName)
			throws ServletException, IOException {
		HttpSession session = req.getSession();

		String newEmail = (String) session.getAttribute(SESSION_NEW_EMAIL);
		String expectedCode = (String) session.getAttribute(SESSION_CODE);
		Long codeTime = (Long) session.getAttribute(SESSION_TIME);
		Integer attempts = (Integer) session.getAttribute(SESSION_ATTEMPTS);

		if (newEmail == null || expectedCode == null || codeTime == null) {
			showError(req, resp, CHANGE_PAGE, "error.email.change.no-pending");
			return;
		}

		// Check if code has expired
		if (System.currentTimeMillis() - codeTime > CODE_EXPIRATION_MS) {
			clearSessionAttributes(session);
			showError(req, resp, CHANGE_PAGE, "error.email.change.code-expired");
			return;
		}

		// Check if too many attempts
		if (attempts != null && attempts >= MAX_ATTEMPTS) {
			clearSessionAttributes(session);
			showError(req, resp, CHANGE_PAGE, "error.email.change.too-many-attempts");
			return;
		}

		String code = req.getParameter("code");
		if (code == null || code.trim().isEmpty()) {
			req.setAttribute(NEW_EMAIL_ATTR, newEmail);
			showError(req, resp, VERIFY_PAGE, "error.email.change.code-empty");
			return;
		}

		// Verify code
		if (!code.trim().equals(expectedCode)) {
			attempts = (attempts == null ? 0 : attempts) + 1;
			session.setAttribute(SESSION_ATTEMPTS, attempts);

			int remainingAttempts = MAX_ATTEMPTS - attempts;
			req.setAttribute(NEW_EMAIL_ATTR, newEmail);
			if (remainingAttempts > 0) {
				showError(req, resp, VERIFY_PAGE, "error.email.change.code-wrong", remainingAttempts);
			} else {
				showError(req, resp, VERIFY_PAGE, "error.email.change.code-wrong-final");
			}
			return;
		}

		// Code is valid, update email
		try {
			DB db = DBService.getInstance();
			try (SqlSession sqlSession = db.openSession()) {
				Users users = sqlSession.getMapper(Users.class);
				users.setEmail(userName, newEmail);
				sqlSession.commit();
			}
			LOG.info("Email changed successfully for user '{}' to '{}'.", userName, newEmail);

			// Clear session attributes
			clearSessionAttributes(session);

			// Redirect to settings with success message
			session.setAttribute("settingsMessage", I18N.getMessage(req, "success.email-change.complete"));
			resp.sendRedirect(req.getContextPath() + SettingsServlet.PATH);

		} catch (Exception ex) {
			LOG.error("Failed to update email for user '{}'.", userName, ex);
			req.setAttribute(NEW_EMAIL_ATTR, newEmail);
			showError(req, resp, VERIFY_PAGE, "error.email.change.update-failed", ex.getMessage());
		}
	}

	private void clearSessionAttributes(HttpSession session) {
		session.removeAttribute(SESSION_NEW_EMAIL);
		session.removeAttribute(SESSION_CODE);
		session.removeAttribute(SESSION_TIME);
		session.removeAttribute(SESSION_ATTEMPTS);
	}

	private void showError(HttpServletRequest req, HttpServletResponse resp, String template, String messageKey, Object... params)
			throws ServletException, IOException {
		req.setAttribute(ERROR_MESSAGE_ATTR, I18N.getMessage(req, messageKey, params));
		TemplateRenderer.getInstance(req).process(template, req, resp);
	}
}
