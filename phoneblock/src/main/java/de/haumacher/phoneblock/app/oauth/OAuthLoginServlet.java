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

import de.haumacher.phoneblock.app.CreateAuthTokenServlet;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.RegistrationServlet;
import de.haumacher.phoneblock.app.SettingsServlet;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.app.render.controller.LoginController;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DB.EmailUser;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.I18N;
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
			
			email = commonProfile.getEmail();
			LOG.info("Received profile (display name given: {}, e-mail given: {}).",
				displayName != null && !displayName.isBlank(), email != null && !email.isBlank());
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
		
		// Try session first (set by PhoneBlockConfigFactory during OAuth redirect),
		// then fall back to URL parameter (present when pac4j redirects back after callback)
		Optional<Object> location = sessionStore.get(context, LoginServlet.LOCATION_ATTRIBUTE);
		if (location.isEmpty()) {
			String locationParam = req.getParameter(LoginServlet.LOCATION_ATTRIBUTE);
			if (locationParam != null && !locationParam.isEmpty()) {
				location = Optional.of(locationParam);
			}
		}

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
					LOG.warn("Received invalid e-mail address (length {}) during Google login of {}.", email.length(), googleId);
					
					// Do not try again, see below.
					email = null;
				}
			}
		}
		
		String passwd = null;
		if (login == null) {
			// Create new account.
			String dialPrefix = DefaultController.selectDialPrefix(req, language);

			if (email != null && !email.isBlank()) {
				// Creates the account together with its e-mail address, so that a concurrent
				// sign-up with the same address does not create a second account (#523).
				try {
					EmailUser account = db.getOrCreateEmailUser(email,
						displayName == null ? email : displayName, language.tag, dialPrefix);
					login = account.login();
					passwd = account.password();
				} catch (AddressException e) {
					LOG.warn("Received invalid e-mail address (length {}) during Google login of {}.", email.length(), googleId);
				}
			}

			if (login == null) {
				login = UUID.randomUUID().toString();
				passwd = db.createUser(login, displayName == null ? login : displayName, language.tag, dialPrefix);
			}

			if (passwd == null) {
				// The account for this e-mail address was created concurrently: link it like an
				// existing account found by its e-mail address above.
				db.setGoogleId(login, googleId, displayName);
			} else {
				db.setGoogleId(login, googleId, null);
			}
		}

		if (passwd != null) {
			// Check if this is a mobile app linking flow (skip credentials page)
			String targetPath = location.isPresent() ? (String) location.get() : null;
			if (targetPath != null && targetPath.startsWith(CreateAuthTokenServlet.MOBILE_LOGIN)) {
				// For mobile flows, redirect directly to token creation without showing credentials
				LoginFilter.setSessionUser(req, db.createMasterLoginToken(login));
				resp.sendRedirect(req.getContextPath() + targetPath);
				return;
			}

			// For web registration, show credentials page
			if (location.isPresent()) {
				req.setAttribute(LoginServlet.LOCATION_ATTRIBUTE, location.get());
			}

			RegistrationServlet.startSetup(req, resp, login, passwd);
			return;
		}
		
		LoginFilter.setSessionUser(req, db.createMasterLoginToken(login));
		
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
		req.setAttribute("error", I18N.getMessage(req, "error.login.failed"));
		TemplateRenderer.getInstance(req).process(LoginController.LOGIN_PAGE, req, resp);
	}

}
