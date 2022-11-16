/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.oauth;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.RegistrationServlet;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;

/**
 * Servlet receiving user profile information after a successful OAuth login.
 */
@WebServlet(urlPatterns = "/oauth/login")
public class OAuthLoginServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(OAuthLoginServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		WebContext context = new JEEContext(req, resp);
		SessionStore sessionStore = JEESessionStoreFactory.INSTANCE.newSessionStore(req, resp);
		ProfileManager manager = new ProfileManager(context, sessionStore);
		Optional<UserProfile> profile = manager.getProfile();		
		if (profile.isEmpty()) {
			sendFailure(req, resp);
			return;
		}
		
		UserProfile userProfile = profile.get();
		String clientName = userProfile.getClientName();
		LOG.info("Received user from client: " + clientName);
		LOG.info("Received user ID: " + userProfile.getId());
		
		String displayName;
		String email;
		if (userProfile instanceof CommonProfile) {
			CommonProfile commonProfile = (CommonProfile) userProfile;
			displayName = commonProfile.getDisplayName();
			LOG.info("Received user name: " + displayName);
			
			email = commonProfile.getEmail();
			LOG.info("Received user e-mail: " + email);
		} else {
			email = null;
			displayName = null;
		}
		
		String extId = userProfile.getId();
		
		DB db = DBService.getInstance();
		String login = db.getLogin(clientName, extId);
		if (login == null) {
			login = UUID.randomUUID().toString();
			
			if (displayName == null) {
				if (email == null) {
					displayName = login;
				} else {
					displayName = email;
				}
			}
			
			String passwd = db.createUser(clientName, extId, login, displayName);
			db.setExtId(login, extId);
			if (email != null) {
				db.setEmail(login, email);
			}
			
			RegistrationServlet.startSetup(req, resp, login, passwd);
		} else {
			LoginFilter.setAuthenticatedUser(req, login);
			resp.sendRedirect(req.getContextPath() + "/settings.jsp");
		}
	}

	/**
	 * Redirects the client to the login page.
	 */
	public static void sendFailure(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setAttribute("error", "Anmeldung fehlgeschlagen.");
		req.getRequestDispatcher("/login.jsp").forward(req, resp);
	}

}
