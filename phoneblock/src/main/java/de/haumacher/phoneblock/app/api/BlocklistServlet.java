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

import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.model.Blocklist;
import de.haumacher.phoneblock.util.ServletUtil;

/**
 * {@link HttpServlet} serving the blocklist.
 */
@WebServlet(urlPatterns = "/api/blocklist")
public class BlocklistServlet extends HttpServlet {
	
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
		Blocklist result = DBService.getInstance().getBlockListAPI(minVotes);
		
		ServletUtil.sendResult(resp, result);
	}

}
