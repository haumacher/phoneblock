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

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.util.ServletUtil;

/**
 * Servlet that adds vote to a phone number comment.
 * 
 * <p>
 * Expected path arguments: Comment ID and <code>up</code>/<code>down</code> command.
 * </p>
 */
@WebServlet(urlPatterns = CommentVoteServlet.PATH)
public class CommentVoteServlet extends HttpServlet {
	
	/**
	 * The path where the {@link CommentVoteServlet} is listening.
	 */
	public static final String PATH = "/api/commentVote";

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String id = req.getParameter("id");
		if (id == null || id.isEmpty()) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing comment ID.");
			return;
		}
		
		String voteText = req.getParameter("vote");
		if (voteText == null || voteText.isEmpty()) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing vote.");
			return;
		}
		
		int vote;
		try {
			vote = Integer.parseInt(voteText);
		} catch (NumberFormatException ex) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid vote.");
			return;
		}
		
		if (vote > 1 || vote < -1) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Vote out of range.");
			return;
		}
		
		int up = up(vote);
		int down = down(vote);

		String oldVoteText = req.getParameter("oldVote");
		if (oldVoteText != null && !oldVoteText.isEmpty()) {
			try {
				int oldVote = Integer.parseInt(oldVoteText);

				if (oldVote > 1 || oldVote < -1) {
					ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Old vote out of range.");
					return;
				}
				
				up -= up(oldVote);
				down -= down(oldVote);
			} catch (NumberFormatException ex) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid old vote.");
				return;
			}
		}
		
		try (SqlSession session = DBService.getInstance().openSession()) {
			SpamReports mapper = session.getMapper(SpamReports.class);
			
			int cnt = mapper.updateCommentVotes(id, up, down);
			if (cnt == 0) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "No such comment.");
				return;
			}
			
			session.commit();
		}
		
		ServletUtil.sendMessage(resp, HttpServletResponse.SC_OK, "Vote recorded.");
	}

	private int down(int vote) {
		return vote < 0 ? -vote : 0;
	}

	private int up(int vote) {
		return vote > 0 ? vote : 0;
	}
	
}
