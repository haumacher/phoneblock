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
import javax.servlet.http.HttpSession;

import de.haumacher.phoneblock.db.DBService;

/**
 * {@link HttpServlet} deleting the account of the currently logged in user.
 */
@WebServlet(urlPatterns = DeleteAccountServlet.PATH)
public class DeleteAccountServlet extends HttpServlet {

	/**
	 * URI where the {@link DeleteAccountServlet} is reachable.
	 */
	public static final String PATH = "/delete-account";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		showSettings(req, resp);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		HttpSession session = req.getSession(false);
		String login = LoginFilter.getAuthenticatedUser(session);
		if (login == null) {
			showSettings(req, resp);
			return;
		}
		
		DBService.getInstance().deleteUser(login);
		resp.sendRedirect(req.getContextPath() + "/logout");
	}

	private void showSettings(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.sendRedirect(req.getContextPath() + "/settings.jsp");
	}
	
}
