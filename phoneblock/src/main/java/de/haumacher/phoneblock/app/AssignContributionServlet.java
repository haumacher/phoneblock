/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBContribution;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;

/**
 * {@link HttpServlet} searching for a contribution that was not automatically assigned to a user.
 */
@WebServlet(urlPatterns = AssignContributionServlet.PATH)
public class AssignContributionServlet extends HttpServlet {
	
	private static final Logger LOG = LoggerFactory.getLogger(AssignContributionServlet.class);
	
	public static final String CONTRIB_DATE = "contrib-date";
	public static final String CONTRIB_NAME = "contrib-name";
	public static final String CONTRIB_TX = "contrib-tx";
	
	/**
	 * URL path where to reach the {@link AssignContributionServlet}.
	 */
	public static final String PATH = "/assign-contribution";
	
	public static final String SECTION_CONTRIBUTIONS = "contributions";
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		showSettings(req, resp);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req.getSession(false));
		if (userName == null) {
			showSettings(req, resp);
			return;
		}
		
		String tx = req.getParameter(CONTRIB_TX);
		String name = req.getParameter(CONTRIB_NAME);
		String date = req.getParameter(CONTRIB_DATE);
		
		DB db = DBService.getInstance();
		
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userIdOpt = users.getUserId(userName);
			
			if (userIdOpt != null) {
				long userId = userIdOpt.longValue();

				if (tx != null) {
					DBContribution contribution = users.getContribution(tx.trim());
					if (contribution != null) {
						if (contribution.getUserId() == null) {
							// Not yet assigned.
							assignUser(users, contribution, userId);
							
							session.commit();
						} else {
							LOG.warn("Contribution {} already assigned to {}, rejecting assign to {}.", contribution.getTx(), contribution.getUserId(), userId);
						}
					} else {
						LOG.warn("Contribution {} not found, cannot assign to {}.", tx, userId);
					}
				} else if (name != null && date != null) {
					String sender = name.trim().toLowerCase().replaceAll(" +", " ");
					try {
						Date received = new SimpleDateFormat("YYYY-MM-dd").parse(date);
						List<DBContribution> contributions = users.searchContribution(sender, received.getTime());
						if (contributions.size() == 1) {
							DBContribution contribution = contributions.get(0);
							assignUser(users, contribution, userId);
							
							session.commit();
						} else {
							LOG.warn("No unique contribution found for {}: name={}, date={}", userId, name, date);
						}
					} catch (ParseException e) {
						LOG.warn("Invalid date received from {}: date={}", userId, date);
					}
				} else {
					LOG.warn("Missing information to assign contribution to {}: tx={}, name={}, date={}", userId, tx, name, date);
				}
			}
		}
		
		showSettings(req, resp);
	}

	private void assignUser(Users users, DBContribution contribution, long userId) {
		int cnt = users.assignContributionUser(contribution.getTx(), userId);
		if (cnt == 1) {
			users.addContribution(userId, contribution.getAmount());
		}
	}

	private void showSettings(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.sendRedirect(req.getContextPath() + SettingsServlet.PATH + "#" + SECTION_CONTRIBUTIONS);
	}

}
