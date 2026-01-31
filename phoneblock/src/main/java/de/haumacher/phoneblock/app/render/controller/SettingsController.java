package de.haumacher.phoneblock.app.render.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.ab.DBAnswerbotInfo;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBAuthToken;
import de.haumacher.phoneblock.db.DBContribution;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.location.Countries;
import de.haumacher.phoneblock.location.model.Country;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class SettingsController extends RequireLoginController {
	
	public static class DialPrefix {
		public final String dialPrefix;
		public final Country country;

		public DialPrefix(String prefix, Country country) {
			this.dialPrefix = prefix;
			this.country = country;
		}
		
		public Country getCountry() {
			return country;
		}
		
		public String getDialPrefix() {
			return dialPrefix;
		}
	}
	
	private static final List<DialPrefix> DIAL_PREFIXES;
	
	static {
		List<DialPrefix> prefixes = new ArrayList<>();
		for (Country country : Countries.all()) {
			for (String dialPrefix : country.getDialPrefixes()) {
				prefixes.add(new DialPrefix(dialPrefix, country));
			}
		}
		prefixes.sort(Comparator.comparing(d -> d.dialPrefix));
		
		DIAL_PREFIXES = prefixes;
	}

	@Override
	protected boolean checkAccessRights(AuthToken authorization) {
		return authorization.isAccessLogin();
	}
	
	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);

		String userName = LoginFilter.getAuthenticatedUser(request);
		UserSettings settings = LoginFilter.getUserSettings(request);

		// Check for success message from session and move to request scope
		HttpSession httpSession = request.getSession(false);
		if (httpSession != null) {
			String settingsMessage = (String) httpSession.getAttribute("settingsMessage");
			if (settingsMessage != null) {
				request.setAttribute("settingsMessage", settingsMessage);
				httpSession.removeAttribute("settingsMessage");
			}
		}

		request.setAttribute("settings", settings);
		request.setAttribute("dialPrefixes", DIAL_PREFIXES);
		
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			
			Long userIdOpt = users.getUserId(userName);
			List<String> blacklist;
			List<String> whitelist;
			List<DBAnswerbotInfo> answerBots;
			List<DBAuthToken> explicitTokens;
			List<DBContribution> contributions;
			if (userIdOpt == null) {
				blacklist = Collections.emptyList();
				whitelist = Collections.emptyList();
				answerBots = Collections.emptyList();
				explicitTokens = Collections.emptyList();
				contributions = Collections.emptyList();
			} else {
				long userId = userIdOpt.longValue();
				
				BlockList blocklist = session.getMapper(BlockList.class);
				blacklist = blocklist.getPersonalizations(userId);
				whitelist = blocklist.getWhiteList(userId);

				answerBots = users.getAnswerBots(userId);
				explicitTokens = users.getExplicitTokens(userId);

				contributions = users.getContributions(userId);
			}
			
			request.setAttribute("blacklist", blacklist);
			request.setAttribute("whitelist", whitelist);
			request.setAttribute("answerBots", answerBots);
			request.setAttribute("explicitTokens", explicitTokens);
			request.setAttribute("contributions", contributions);
		}
	}

}
