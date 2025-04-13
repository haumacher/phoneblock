package de.haumacher.phoneblock.app.render.controller;

import java.util.Collections;
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
import de.haumacher.phoneblock.db.settings.UserSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class SettingsController extends RequireLoginController {
	
	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);
		
		
		HttpSession httpSession = request.getSession(false);
		String userName = LoginFilter.getAuthenticatedUser(httpSession);
		UserSettings settings = DBService.getInstance().getSettings(userName);
		
		request.setAttribute("settings", settings);
		
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
