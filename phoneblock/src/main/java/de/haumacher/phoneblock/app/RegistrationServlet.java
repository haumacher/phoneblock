/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DBService;

/**
 * {@link HttpServlet} invoked from the <code>signup-code.jsp</code> form after the e-mail verification code has been
 * entered.
 */
@WebServlet(urlPatterns = "/registration-code")
public class RegistrationServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(RegistrationServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
		String passwd;
		try {
			passwd = DBService.getInstance().createUser(email);
		} catch (Exception ex) {
			LOG.error("Failed to create user: " + email, ex);

			req.setAttribute("message", "Bei der Erstellung des Accounts ist ein Fehler aufgetreten: " + ex.getMessage());
			req.getRequestDispatcher("/signup-code.jsp").forward(req, resp);
			return;
		}
		
		LoginFilter.setAuthenticatedUser(req, email);
		
		req.setAttribute("email", email);
		req.setAttribute("token", passwd);
		req.getRequestDispatcher("/setup.jsp").forward(req, resp);
	}
}
