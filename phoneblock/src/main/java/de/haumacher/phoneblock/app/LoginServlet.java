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

import de.haumacher.phoneblock.db.DBService;

/**
 * Servlet displaying information about a telephone number in the DB.
 */
@WebServlet(urlPatterns = "/login")
public class LoginServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.sendRedirect(req.getContextPath() + "/login.jsp");
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = req.getParameter("userName");
		String password = req.getParameter("password");
		
		if (userName == null || userName.isEmpty()) {
			System.out.println("Login without user name.");
			sendFailure(req, resp);
			return;
		}
		
		if (password == null || password.isEmpty()) {
			System.out.println("Login without password.");
			sendFailure(req, resp);
			return;
		}
		
		String authenticatedUser = DBService.getInstance().login(userName, password);
		if (authenticatedUser == null) {
			System.out.println("Login failed for user: " + userName);
			sendFailure(req, resp);
			return;
		}
		
		LoginFilter.setAuthenticatedUser(req, authenticatedUser);
		resp.sendRedirect(req.getContextPath() + "/settings.jsp");
	}

	private void sendFailure(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setAttribute("error", "Anmeldung fehlgeschlagen.");
		req.getRequestDispatcher("/login.jsp").forward(req, resp);
	}

}
