/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.render.controller;

import java.util.Collections;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.UserSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Base controller for the dedicated personal blacklist and whitelist pages.
 *
 * <p>The two lists were originally rendered inline on the settings page; pulling
 * them onto their own routes keeps the settings page short once a user has
 * accumulated many entries.</p>
 */
public abstract class PersonalListController extends RequireLoginController {

	@Override
	protected boolean checkAccessRights(AuthToken authorization) {
		return authorization.isAccessLogin();
	}

	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);

		String userName = LoginFilter.getAuthenticatedUser(request);
		UserSettings settings = LoginFilter.getUserSettings(request);

		HttpSession httpSession = request.getSession(false);
		if (httpSession != null) {
			String settingsMessage = (String) httpSession.getAttribute("settingsMessage");
			if (settingsMessage != null) {
				request.setAttribute("settingsMessage", settingsMessage);
				httpSession.removeAttribute("settingsMessage");
			}
		}

		request.setAttribute("settings", settings);

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userIdOpt = users.getUserId(userName);
			List<String> entries;
			if (userIdOpt == null) {
				entries = Collections.emptyList();
			} else {
				BlockList blocklist = session.getMapper(BlockList.class);
				entries = loadEntries(blocklist, userIdOpt.longValue());
			}
			request.setAttribute(attributeName(), entries);
		}
	}

	/**
	 * Loads the entries of this controller's personal list (black- or whitelist).
	 */
	protected abstract List<String> loadEntries(BlockList blocklist, long userId);

	/**
	 * Name of the request attribute under which the entries are exposed to the template.
	 */
	protected abstract String attributeName();

}
