/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.ab.DBAnswerbotInfo;
import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.carddav.resource.AddressBookCache;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBAuthToken;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet updating user settings.
 */
@WebServlet(urlPatterns = SettingsServlet.PATH)
public class SettingsServlet extends HttpServlet {
	
	public static final String API_KEY_LABEL_PARAM = "label";
	public static final String KEY_ID_PREFIX = "key-";
	public static final String PATH = "/settings";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req.getSession());
		if (userName == null) {
			LoginServlet.requestLogin(req, resp);
			return;
		}
		
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			
			Long userIdOpt = users.getUserId(userName);
			List<String> blacklist;
			List<String> whitelist;
			List<DBAnswerbotInfo> answerBots;
			List<DBAuthToken> explicitTokens;
			if (userIdOpt == null) {
				blacklist = Collections.emptyList();
				whitelist = Collections.emptyList();
				answerBots = Collections.emptyList();
				explicitTokens = Collections.emptyList();;
			} else {
				long userId = userIdOpt.longValue();
				
				BlockList blocklist = session.getMapper(BlockList.class);
				blacklist = blocklist.getPersonalizations(userId);
				whitelist = blocklist.getWhiteList(userId);

				answerBots = users.getAnswerBots(userId);
				explicitTokens = users.getExplicitTokens(userId);
			}
			
			req.setAttribute("blacklist", blacklist);
			req.setAttribute("whitelist", whitelist);
			req.setAttribute("answerBots", answerBots);
			req.setAttribute("explicitTokens", explicitTokens);
		}
		
		ServletUtil.display(req, resp, "/settings.jsp");
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req.getSession());
		if (userName == null) {
			sendFailure(req, resp);
			return;
		}
		
		String action = req.getParameter("action");
		if (action == null) {
			updateSettings(req, resp, userName);
		} else {
			switch (action) {
				case "lists":
					updateLists(req, resp, userName);
					return;
					
				case "deleteAPIKeys":
					deleteAPIKeys(req, resp, userName);
					return;
					
				case "createAPIKey":
					createAPIKey(req, resp, userName);
					return;
					
				default: 
					resp.sendRedirect(req.getContextPath() + SettingsServlet.PATH);
			}
		}
	}

	private void createAPIKey(HttpServletRequest req, HttpServletResponse resp, String userName) throws ServletException, IOException {
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			if (userId != null) {
				long now = System.currentTimeMillis();
				AuthToken apiKey = DB.createAuthorizationTemplate(userName, now, null);
				apiKey.setAccessDownload(true).setAccessQuery(true).setAccessRate(true);
				String label = req.getParameter(API_KEY_LABEL_PARAM);
				if (label == null) {
					label = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(now)) + 
						" - " + req.getHeader("User-Agent") + 
						" - " + req.getRemoteAddr();
				}
				apiKey.setLabel(label);
				db.createAuthToken(apiKey);
				session.commit();
				
				req.setAttribute("apiKey", apiKey);
				req.getRequestDispatcher("/show-api-key.jsp").forward(req, resp);
			}
		}
	}

	private void deleteAPIKeys(HttpServletRequest req, HttpServletResponse resp, String userName) {
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			if (userId != null) {
				for (Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
					String key = entry.getKey();
					if (key.startsWith(KEY_ID_PREFIX)) {
						String id = key.substring(KEY_ID_PREFIX.length());
						
						users.deleteAuthToken(userId.longValue(), Long.parseLong(id));
					}
				}
				
				session.commit();
			}
		}
	}

	private void updateLists(HttpServletRequest req, HttpServletResponse resp, String userName) throws IOException {
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			if (userId != null) {
				int owner = userId.intValue();
				
				BlockList blocklist = session.getMapper(BlockList.class);
				for (Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
					String key = entry.getKey();
					if (key.equals("add-wl")) {
						String addValues = entry.getValue()[0];
						for (String value : addValues.split("[,;]")) {
							String phone = NumberAnalyzer.toId(value);
							if (phone == null) {
								continue;
							}
							
							blocklist.removePersonalization(owner, phone);
							blocklist.addExclude(owner, phone);
						}
					}
					else if (key.startsWith("bl-")) {
						String phone = NumberAnalyzer.toId(key.substring("bl-".length()));
						if (phone == null) {
							continue;
						}
						
						blocklist.removePersonalization(owner, phone);
					}
					
					else if (key.startsWith("wl-")) {
						String phone = NumberAnalyzer.toId(key.substring("wl-".length()));
						if (phone == null) {
							continue;
						}
						
						blocklist.removePersonalization(owner, phone);
					}
				}
			}
			
			session.commit();
		}
		
		AddressBookCache.getInstance().flushUserCache(userName);
		
		resp.sendRedirect(req.getContextPath() + SettingsServlet.PATH);
	}

	private void updateSettings(HttpServletRequest req, HttpServletResponse resp, String userName) throws IOException {
		int minVotes = Integer.parseInt(req.getParameter("minVotes"));
		int maxLength = Integer.parseInt(req.getParameter("maxLength"));
		boolean wildcards = req.getParameter("wildcards") != null;
		
		if (minVotes <= 2) {
			minVotes = 2;
		}
		else if (minVotes <= 4) {
			minVotes = 4;
		}
		else if (minVotes <= 10) {
			minVotes = 10;
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
		settings.setWildcards(wildcards);
		db.updateSettings(settings);
		
		// Ensure that a new block list is created, if the user is experimenting with the possible block list size.
		AddressBookCache.getInstance().flushUserCache(userName);
		
		resp.sendRedirect(req.getContextPath() + SettingsServlet.PATH);
	}

	private void sendFailure(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setAttribute("error", "Anmeldung fehlgeschlagen.");
		req.getRequestDispatcher("/login.jsp").forward(req, resp);
	}

}
