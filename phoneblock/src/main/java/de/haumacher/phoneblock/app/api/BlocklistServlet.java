/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link HttpServlet} serving the blocklist.
 */
@WebServlet(urlPatterns = BlocklistServlet.PATH)
public class BlocklistServlet extends HttpServlet {
	
	public static final String PATH = "/api/blocklist";
	
	private static final Logger LOG = LoggerFactory.getLogger(BlocklistServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			ServletUtil.sendAuthenticationRequest(resp);
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
			
			if (minVotes < 2) {
				ServletUtil.sendError(resp, "Parameter minVotes must be 2 or greater.");
				return;
			}
		}
		DB db = DBService.getInstance();
		Blocklist result = db.getBlockListAPI(minVotes);
		
		String userAgent = req.getHeader("User-Agent");
		LOG.info("Sending blocklist to user '" + userName + "' (agent '" + userAgent + "')");
		db.updateLastAccess(userName, System.currentTimeMillis(), userAgent);
		
		ServletUtil.sendResult(req, resp, result);
	}

}
