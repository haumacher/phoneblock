package de.haumacher.phoneblock.app.render.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.ab.DBAnswerbotInfo;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBAuthToken;
import de.haumacher.phoneblock.db.DBContribution;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.UserSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class RequireLoginController extends DefaultController {
	
	@Override
	public void process(TemplateRenderer renderer, HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		HttpSession httpSession = request.getSession(false);
		String userName = LoginFilter.getAuthenticatedUser(httpSession);
		if (userName == null) {
			LoginServlet.requestLogin(request, response);
			return;
		}
		
		super.process(renderer, request, response);
	}

}
