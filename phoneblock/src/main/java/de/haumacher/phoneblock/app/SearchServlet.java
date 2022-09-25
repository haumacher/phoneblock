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

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.analysis.PhoneNumer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReport;

/**
 * Servlet displaying information about a telephone number in the DB.
 */
@WebServlet(urlPatterns = "/nums/*")
public class SearchServlet extends HttpServlet {
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() < 1) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		String phone = pathInfo.substring(1).replaceAll("[^\\+0-9]", "");
		if (phone.isEmpty()) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		PhoneNumer number = NumberAnalyzer.analyze(phone);
		
		String shortcut = number.getShortcut();
		SpamReport info = DBService.getInstance().getPhoneInfo(shortcut == null ? number.getZeroZero() : shortcut);
		if (info == null) {
			info = new SpamReport(phone, 0, 0, 0);
		}
		req.setAttribute("info", info);
		req.setAttribute("number", number);
		req.setAttribute("title", status(info.getVotes()) + ": Rufnummer ☎ " + phone + " - PhoneBlock");
		
		req.getRequestDispatcher("/phone-info.jsp").forward(req, resp);
	}

	private String status(int votes) {
		if (votes == 0) {
			return "Keine Beschwerden";
		} else if (votes < DB.MIN_VOTES) {
			return "Spamverdacht";
		} else {
			return "Blockiert";
		}
	}

}
