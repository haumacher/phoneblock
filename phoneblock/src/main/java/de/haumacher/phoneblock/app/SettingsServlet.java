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

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.UserSettings;

/**
 * Servlet updating user settings.
 */
@WebServlet(urlPatterns = "/settings")
public class SettingsServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.sendRedirect(req.getContextPath() + "/settings.jsp");
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req.getSession());
		if (userName == null) {
			sendFailure(req, resp);
			return;
		}
		
		int minVotes = Integer.parseInt(req.getParameter("minVotes"));
		int maxLength = Integer.parseInt(req.getParameter("maxLength"));
		
		if (minVotes <= 4) {
			minVotes = 4;
		}
		else if (minVotes <= 8) {
			minVotes = 8;
		}
		else if (minVotes <= 20) {
			minVotes = 20;
		}
		else {
			minVotes = 100;
		}
		
		if (maxLength <= 1000) {
			maxLength = 1000;
		} 
		else if (maxLength <= 2000) {
			maxLength = 2000;
		} 
		else if (maxLength <= 3000) {
			maxLength = 3000;
		}
		else if (maxLength <= 4000) {
			maxLength = 4000;
		} 
		else if (maxLength <= 5000) {
			maxLength = 5000;
		} 
		else {
			maxLength = 6000;
		}
		
		DB db = DBService.getInstance();
		UserSettings settings = db.getSettings(userName);
		settings.setMinVotes(minVotes);
		settings.setMaxLength(maxLength);
		db.updateSettings(settings);
		
		resp.sendRedirect(req.getContextPath() + "/settings.jsp");
	}

	private void sendFailure(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setAttribute("error", "Anmeldung fehlgeschlagen.");
		req.getRequestDispatcher("/login.jsp").forward(req, resp);
	}

}
