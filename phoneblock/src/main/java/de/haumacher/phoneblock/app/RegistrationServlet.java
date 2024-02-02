/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;

/**
 * {@link HttpServlet} invoked from the <code>signup-code.jsp</code> form after the e-mail verification code has been
 * entered.
 */
@WebServlet(urlPatterns = "/registration-code")
public class RegistrationServlet extends HttpServlet {

	private static final String PASSWORD_ATTR = "passwd";

	/**
	 * The authorization scope "email".
	 */
	public static final String IDENTIFIED_BY_EMAIL = "email";
	
	private static final Logger LOG = LoggerFactory.getLogger(RegistrationServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		LoginServlet.forwardLocation(req);
		
		Object expectedCode = req.getSession().getAttribute("code");
		if (expectedCode == null) {
			req.setAttribute("message", "Der Bestätigungscode ist abgelaufen. Bitte starte die Registrierung erneut.");
			req.getRequestDispatcher("/signup-code.jsp").forward(req, resp);
			return;
		}
		
		String code = req.getParameter("code");
		if (code == null || code.trim().isEmpty() || !code.equals(expectedCode)) {
			req.setAttribute("message", "Der Bestätigungscode stimmt nicht überein.");
			req.getRequestDispatcher("/signup-code.jsp").forward(req, resp);
			return;
		}
		
		String email = (String) req.getSession().getAttribute("email");
		
		String login;
		String passwd;
		try {
			DB db = DBService.getInstance();
			String extId = email.trim().toLowerCase();
			login = db.getLogin(RegistrationServlet.IDENTIFIED_BY_EMAIL, extId);
			if (login == null) {
				login = UUID.randomUUID().toString();
				passwd = db.createUser(IDENTIFIED_BY_EMAIL, extId, login, email);
				db.setEmail(login, email);
			} else {
				passwd = db.resetPassword(login);
			}
		} catch (Exception ex) {
			LOG.error("Failed to create user: " + email, ex);

			req.setAttribute("message", "Bei der Erstellung des Accounts ist ein Fehler aufgetreten: " + ex.getMessage());
			req.getRequestDispatcher("/signup-code.jsp").forward(req, resp);
			return;
		}
		
		startSetup(req, resp, login, passwd);
	}

	/** 
	 * Displays the setup page.
	 */
	public static void startSetup(HttpServletRequest req, HttpServletResponse resp,
			String login, String passwd) throws ServletException, IOException {
		LoginFilter.setAuthenticatedUser(req, login);
		req.getSession().setAttribute(PASSWORD_ATTR, passwd);
		
		String location = LoginServlet.location(req);
		if (location != null) {
			resp.sendRedirect(req.getContextPath() + location);
		} else {
			resp.sendRedirect(req.getContextPath() + "/setup.jsp");
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
