/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import de.haumacher.phoneblock.app.SearchServlet;
import de.haumacher.phoneblock.app.api.model.SearchResult;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet for search API returning the info from the web search in a machine readable form.
 */
@WebServlet(urlPatterns = SearchApiServlet.PREFIX + "/*")
public class SearchApiServlet extends HttpServlet {

	static final String PREFIX = "/api/search";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() < 1) {
			ServletUtil.sendError(resp, "Missing phone number.");
			return;
		}
		
		String query = pathInfo.substring(1);
		SearchResult searchResult = SearchServlet.analyze(query);
		
		if (searchResult == null) {
			ServletUtil.sendError(resp, "Invalid phone number.");
			return;
		}
		
		ServletUtil.sendResult(req, resp, searchResult);
	}

}
