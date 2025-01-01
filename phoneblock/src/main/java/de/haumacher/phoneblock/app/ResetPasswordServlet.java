/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import de.haumacher.phoneblock.db.DBService;

/**
 * {@link HttpServlet} creating a new password for the currently logged in user.
 */
@WebServlet(urlPatterns = ResetPasswordServlet.PATH)
public class ResetPasswordServlet extends HttpServlet {
	
	/**
	 * URL path where to reach the {@link ResetPasswordServlet}.
	 */
	public static final String PATH = "/reset-password";
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		showSettings(req, resp);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));
		if (login == null) {
			showSettings(req, resp);
			return;
		}
		
		String password = DBService.getInstance().resetPassword(login);
		if (password == null) {
			req.setAttribute("message", "Nutzername nicht gefunden, bitte versuch Dich neu zu registrieren.");
			req.getRequestDispatcher("/login.jsp").forward(req, resp);
			return;
		}
		
		RegistrationServlet.startSetup(req, resp, login, password);
	}

	private void showSettings(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.sendRedirect(req.getContextPath() + SettingsServlet.PATH);
	}

}
