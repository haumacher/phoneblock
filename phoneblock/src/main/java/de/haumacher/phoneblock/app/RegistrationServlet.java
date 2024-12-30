/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;

/**
 * {@link HttpServlet} invoked from the signup page when the e-mail verification code is entered.
 */
@WebServlet(urlPatterns = {
	RegistrationServlet.REGISTER_WEB,
})
public class RegistrationServlet extends HttpServlet {

	public static final String REGISTER_WEB = "/register-web";

	private static final String PASSWORD_ATTR = "passwd";

	private static final Logger LOG = LoggerFactory.getLogger(RegistrationServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		LoginServlet.forwardLocation(req);
		
		Object expectedCode = req.getSession().getAttribute("code");
		if (expectedCode == null) {
			sendError(req, resp, "Der Bestätigungscode ist abgelaufen. Bitte starte die Registrierung erneut.");
			return;
		}
		
		String code = req.getParameter("code");
		if (code == null || code.trim().isEmpty() || !code.equals(expectedCode)) {
			sendError(req, resp, "Der Bestätigungscode stimmt nicht überein.");
			return;
		}
		
		String email = (String) req.getSession().getAttribute("email");
		
		String login;
		try {
			DB db = DBService.getInstance();
			login = db.getEmailLogin(email);
			if (login == null) {
				login = UUID.randomUUID().toString();
				String passwd = db.createUser(login, email);
				db.setEmail(login, email);
				startSetup(req, resp, login, passwd);
			} else {
				startSetup(req, resp, login, null);
			}
		} catch (Exception ex) {
			LOG.error("Failed to create user: " + email, ex);

			sendError(req, resp, "Bei der Erstellung des Accounts ist ein Fehler aufgetreten: " + ex.getMessage());
			return;
		}
	}

	/** 
	 * Displays the setup page.
	 */
	public static void startSetup(HttpServletRequest req, HttpServletResponse resp,
			String login, String passwd) throws ServletException, IOException {
		LoginFilter.setAuthenticatedUser(req, login);
		if (passwd != null) {
			req.getSession().setAttribute(PASSWORD_ATTR, passwd);
		}
		
		String location = LoginServlet.location(req);
		if (location != null) {
			resp.sendRedirect(req.getContextPath() + location);
		} else {
			if (passwd == null) {
				// Was already registered, no automatic password-reset.
				resp.sendRedirect(req.getContextPath() + SettingsServlet.PATH);
			} else {
				resp.sendRedirect(req.getContextPath() + successPage(req));
			}
		}
	}

	private static String successPage(HttpServletRequest req) {
		switch (req.getServletPath()) {
		case REGISTER_WEB:
		default:
			return "/setup.jsp";
		}
	}
	
	private void sendError(HttpServletRequest req, HttpServletResponse resp, String message) throws ServletException, IOException {
		req.setAttribute("message", message);
		req.getRequestDispatcher(errorPage(req)).forward(req, resp);
	}

	private String errorPage(HttpServletRequest req) {
		switch (req.getServletPath()) {
		case REGISTER_WEB:
		default:
			return "/signup-code.jsp";
		}
	}

	/**
	 * The password that was newly assigned.
	 */
	public static String getPassword(HttpSession session) {
		if (session == null) {
			return null;
		}
		return (String) session.getAttribute(PASSWORD_ATTR);
	}
}
