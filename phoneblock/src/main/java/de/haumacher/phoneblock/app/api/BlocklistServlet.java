/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.model.Blocklist;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link HttpServlet} serving the blocklist.
 */
@WebServlet(urlPatterns = "/api/blocklist")
public class BlocklistServlet extends HttpServlet {
	
	private static final Logger LOG = LoggerFactory.getLogger(BlocklistServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (!ServletUtil.checkAuthentication(req, resp)) {
			return;
		}
		
		int minVotes = 4;
		String minVotesParam = req.getParameter("minVotes");
		if (minVotesParam != null) {
			try {
				minVotes = Integer.parseInt(minVotesParam);
			} catch (NumberFormatException ex) {
				ServletUtil.sendError(resp, "Invalid minVotes parameter.");
				return;
			}
		}
		DB db = DBService.getInstance();
		Blocklist result = db.getBlockListAPI(minVotes);
		
		String userAgent = req.getHeader("User-Agent");
		String userName = (String) req.getAttribute(LoginFilter.AUTHENTICATED_USER_ATTR);
		LOG.info("Sending blocklist to user '" + userName + "' (agent '" + userAgent + "')");
		db.updateLastAccess(userName, System.currentTimeMillis(), userAgent);
		
		ServletUtil.sendResult(req, resp, result);
	}

}
