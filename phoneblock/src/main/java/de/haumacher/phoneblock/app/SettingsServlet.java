/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.analysis.PhoneNumer;
import de.haumacher.phoneblock.carddav.resource.AddressBookCache;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.UserSettings;

/**
 * Servlet updating user settings.
 */
@WebServlet(urlPatterns = SettingsServlet.PATH)
public class SettingsServlet extends HttpServlet {
	
	public static final String PATH = "/settings";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req.getSession());
		
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			List<String> blacklist;
			List<String> whitelist;
			if (userId == null) {
				blacklist = Collections.emptyList();
				whitelist = Collections.emptyList();
			} else {
				BlockList blocklist = session.getMapper(BlockList.class);
				blacklist = blocklist.getPersonalizations(userId.longValue());
				whitelist = blocklist.getWhiteList(userId.longValue());
			}
			
			req.setAttribute("blacklist", blacklist);
			req.setAttribute("whitelist", whitelist);
		}
		
		req.getRequestDispatcher("/settings.jsp").forward(req, resp);
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
					
				default: 
					resp.sendRedirect(req.getContextPath() + "/settings");
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
							String phone = normalize(value);
							if (phone == null) {
								continue;
							}
							
							blocklist.removePersonalization(owner, phone);
							blocklist.removeExclude(owner, phone);
							blocklist.addExclude(owner, phone);
						}
					}
					else if (key.startsWith("bl-")) {
						String phone = normalize(key.substring("bl-".length()));
						if (phone == null) {
							continue;
						}
						
						blocklist.removePersonalization(owner, phone);
					}
					
					else if (key.startsWith("wl-")) {
						String phone = normalize(key.substring("wl-".length()));
						if (phone == null) {
							continue;
						}
						
						blocklist.removeExclude(owner, phone);
					}
				}
			}
			
			session.commit();
		}
		
		AddressBookCache.getInstance().flushUserCache(userName);
		
		resp.sendRedirect(req.getContextPath() + "/settings");
	}

	private String normalize(String value) {
		String phone = NumberAnalyzer.normalizeNumber(value);
		if (phone.isEmpty() || phone.contains("*")) {
			return null;
		}
		
		PhoneNumer number = NumberAnalyzer.analyze(phone);
		if (number == null) {
			return null;
		}
		return NumberAnalyzer.getPhoneId(number);
	}

	private void updateSettings(HttpServletRequest req, HttpServletResponse resp, String userName) throws IOException {
		int minVotes = Integer.parseInt(req.getParameter("minVotes"));
		int maxLength = Integer.parseInt(req.getParameter("maxLength"));
		
		if (minVotes <= 4) {
			minVotes = 4;
		}
		else if (minVotes <= 8) {
			minVotes = 8;
		}
		else if (minVotes <= 20) {
			minVotes = 20;
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
		db.updateSettings(settings);
		
		// Ensure that a new block list is created, if the user is experimenting with the possible block list size.
		AddressBookCache.getInstance().flushUserCache(userName);
		
		resp.sendRedirect(req.getContextPath() + "/settings");
	}

	private void sendFailure(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setAttribute("error", "Anmeldung fehlgeschlagen.");
		req.getRequestDispatcher("/login.jsp").forward(req, resp);
	}

}
