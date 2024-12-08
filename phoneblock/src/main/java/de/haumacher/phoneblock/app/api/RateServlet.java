/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.RateRequest;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet that adds a rating to a phone number.
 */
@WebServlet(urlPatterns = "/api/rate")
public class RateServlet extends HttpServlet {
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (!ServletUtil.checkAuthentication(req, resp)) {
			return;
		}

		RateRequest rateRequest = RateRequest.readRateRequest(new JsonReader(new ReaderAdapter(req.getReader())));

		String phoneText = rateRequest.getPhone();
		
		String phoneId = NumberAnalyzer.toId(phoneText);
		if (phoneId == null) {
			ServletUtil.sendError(resp, "Invalid phone number.");
			return;
		}

		Rating rating = rateRequest.getRating();
		DBService.getInstance().addRating(phoneId, rating, rateRequest.getComment(), System.currentTimeMillis());
		
		ServletUtil.sendMessage(resp, HttpServletResponse.SC_OK, "Rating recorded.");
	}
	
}
