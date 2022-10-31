/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.analysis.PhoneNumer;
import de.haumacher.phoneblock.app.SearchServlet;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.model.PhoneInfo;
import de.haumacher.phoneblock.util.ServletUtil;

/**
 * Servlet for live API lookup of ratings.
 */
@WebServlet(urlPatterns = NumServlet.PREFIX + "/*")
public class NumServlet extends HttpServlet {

	static final String PREFIX = "/api/num";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (!ServletUtil.checkAuthentication(req, resp)) {
			return;
		}

		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() < 1) {
			ServletUtil.sendError(resp, "Missing phone number.");
			return;
		}
		
		String phone = pathInfo.substring(1).replaceAll("[^\\+0-9]", "");
		if (phone.isEmpty()) {
			ServletUtil.sendError(resp, "Invalid phone number.");
			return;
		}
		
		PhoneNumer number = NumberAnalyzer.analyze(phone);
		if (number == null) {
			ServletUtil.sendError(resp, "Invalid phone number.");
			return;
		}
		
		DB db = DBService.getInstance();
		String phoneId = SearchServlet.getPhoneId(number);
		
		if (!SearchServlet.isBot(req)) {
			db.addSearchHit(phoneId);
		}
		
		PhoneInfo info = db.getPhoneApiInfo(phoneId);
		
		ServletUtil.sendResult(resp, info);
	}

}