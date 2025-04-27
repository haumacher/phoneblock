/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.oauth;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.pac4j.core.context.FrameworkParameters;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.JEEFrameworkParameters;
import org.pac4j.jee.context.session.JEESessionStoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.RegistrationServlet;
import de.haumacher.phoneblock.app.SettingsServlet;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.shared.Language;
import jakarta.mail.internet.AddressException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet receiving user profile information after a successful OAuth login.
 */
@WebServlet(urlPatterns = OAuthLoginServlet.OAUTH_LOGIN_PATH)
public class OAuthLoginServlet extends HttpServlet {

	/**
	 * The path to which an OAuth server redirects after login.
	 */
	public static final String OAUTH_LOGIN_PATH = "/oauth/login";
	
	private static final Logger LOG = LoggerFactory.getLogger(OAuthLoginServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		WebContext context = new JEEContext(req, resp);
		FrameworkParameters parameters = new JEEFrameworkParameters(req, resp);
		SessionStore sessionStore = JEESessionStoreFactory.INSTANCE.newSessionStore(parameters);
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
		Locale locale;
		if (userProfile instanceof CommonProfile) {
			CommonProfile commonProfile = (CommonProfile) userProfile;
			displayName = commonProfile.getDisplayName();
			locale = commonProfile.getLocale();
			LOG.info("Received user name: " + displayName);
			
			email = commonProfile.getEmail();
			LOG.info("Received user e-mail: " + email);
		} else {
			email = null;
			displayName = null;
			locale = null;
		}
		
		Language language;
		if (locale == null) {
			language = DefaultController.selectLanguage(req);
		} else {
			language = DefaultController.selectLanguage(locale);
		}
		
		String googleId = userProfile.getId();
		
		Optional<Object> location = sessionStore.get(context, LoginServlet.LOCATION_ATTRIBUTE);
		
		DB db = DBService.getInstance();
		String login = db.getGoogleLogin(googleId);
		if (login == null) {
			if (email != null && !email.isBlank()) {
				try {
					login = db.getEmailLogin(email);
					if (login != null) {
						// Link accounts.
						db.setGoogleId(login, googleId, displayName);
					}
				} catch (AddressException e) {
					LOG.warn("Reveived invalid e-mail address during Google login of {} login: {}", googleId, email);
					
					// Do not try again, see below.
					email = null;
				}
			}
		}
		
		if (login == null) {
			// Create new account.
			login = UUID.randomUUID().toString();
			
			if (displayName == null) {
				if (email == null) {
					displayName = login;
				} else {
					displayName = email;
				}
			}
			
			String dialPrefix = DefaultController.selectDialPrefix(req, language);
			
			String passwd = db.createUser(login, displayName, language.tag, dialPrefix);
			db.setGoogleId(login, googleId, null);
			if (email != null) {
				try {
					db.setEmail(login, email);
				} catch (AddressException e) {
					LOG.warn("Reveived invalid e-mail address during Google login of {} login: {}", googleId, email);
				}
			}
			
			if (location.isPresent()) {
				req.setAttribute(LoginServlet.LOCATION_ATTRIBUTE, location.get());
			}
			
			RegistrationServlet.startSetup(req, resp, login, passwd);
			return;
		}
		
		LoginFilter.setSessionUser(req, login);
		
		Optional<Object> remember = sessionStore.get(context, LoginServlet.REMEMBER_ME_PARAM);
		if (remember.isPresent()) {
			LoginServlet.processRememberMe(req, resp, db, (String) remember.get(), login);
		}
		
		String path = location.isEmpty() ? SettingsServlet.PATH : (String) location.get();
		resp.sendRedirect(req.getContextPath() + path);
	}

	/**
	 * Redirects the client to the login page.
	 */
	public static void sendFailure(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setAttribute("error", "Anmeldung fehlgeschlagen.");
		TemplateRenderer.getInstance(req).process("/login", req, resp);
	}

}
