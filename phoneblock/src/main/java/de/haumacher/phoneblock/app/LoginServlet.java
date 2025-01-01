/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.util.ServletUtil;

/**
 * Servlet displaying information about a telephone number in the DB.
 */
@WebServlet(urlPatterns = LoginServlet.PATH)
public class LoginServlet extends HttpServlet {
	
	/**
	 * Request attribute set, if a login was not successful.
	 */
	public static final String LOGIN_ERROR_ATTR = "loginError";

	public static final String USER_NAME_PARAM = "userName";

	public static final String PASSWORD_PARAM = "password";

	/**
	 * Request parameter that makes the login persistent, if its value is <code>true</code>.
	 */
	public static final String REMEMBER_PARAM = "remember";

	/**
	 * Request attribute that save the original location that was requested before login.
	 * 
	 * <p>
	 * The location is a path relative to the context path of the application.
	 * </p>
	 * 
	 * <p>
	 * The value is transmitted in the login request as additional parameter.
	 * </p>
	 */
	public static final String LOCATION_ATTRIBUTE = "locationAfterLogin";
	
	public static final String PATH = "/login";

	private static final Logger LOG = LoggerFactory.getLogger(LoginServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (LoginFilter.getAuthenticatedUser(req.getSession(false)) != null) {
			String location = location(req);
			if (location == null) {
				location = SettingsServlet.PATH;
			}
			resp.sendRedirect(req.getContextPath() + location);
			return;
		}
		req.getRequestDispatcher("/login.jsp").forward(req, resp);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = req.getParameter(USER_NAME_PARAM);
		String password = req.getParameter(PASSWORD_PARAM);
		
		if (userName == null || userName.isEmpty()) {
			LOG.info("Login without user name.");
			sendFailure(req, resp);
			return;
		}
		
		if (password == null || password.isEmpty()) {
			LOG.info("Login without password.");
			sendFailure(req, resp);
			return;
		}
		
		DB db = DBService.getInstance();
		String authenticatedUser = db.login(userName, password);
		if (authenticatedUser == null) {
			LOG.warn("Login failed for user: " + userName);
			sendFailure(req, resp);
			return;
		}

		String rememberValue = req.getParameter(REMEMBER_PARAM);
		processRememberMe(req, resp, db, rememberValue, userName);
		
		LoginFilter.setAuthenticatedUser(req, authenticatedUser);
		
		redirectToLocationAfterLogin(req, resp);
	}

	/**
	 * Redirects the current request to its final destination.
	 */
	public static void redirectToLocationAfterLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String location = location(req);
		if (location == null) {
			resp.sendRedirect(req.getContextPath() + SettingsServlet.PATH);
		} else {
			resp.sendRedirect(req.getContextPath() + location);
		}
	}

	public static void processRememberMe(HttpServletRequest req, HttpServletResponse resp, DB db, String rememberValue,
			String userName) {
		boolean rememberMe = "true".equals(rememberValue);
		if (rememberMe) {
			AuthToken authorization = db.createAuthorizationTemplate(userName, System.currentTimeMillis(), req.getHeader("User-Agent"));
			
			db.createAuthToken(authorization);
			
			LoginFilter.setLoginCookie(req, resp, authorization);
		}
	}

	/**
	 * Redirects the client to the login page.
	 */
	public static void sendFailure(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setAttribute(LOGIN_ERROR_ATTR, "Anmeldung fehlgeschlagen.");
		req.getRequestDispatcher("/login.jsp").forward(req, resp);
	}

	/**
	 * Encodes URL parameter transporting the location after login to the next link invocation.
	 */
	public static String locationParam(HttpServletRequest request) {
		return locationParam(request, false);
	}
	
	/**
	 * Encodes URL parameter transporting the location after login to the next link invocation.
	 */
	public static String locationParamFirst(HttpServletRequest request) {
		return locationParam(request, true);
	}
	
	/**
	 * Encodes URL parameter transporting the location after login to the next link invocation.
	 */
	private static String locationParam(HttpServletRequest request, boolean first) {
		return locationParam(location(request), first);
	}

	/**
	 * Creates an URL parameter transporting the location after login to the next link invocation.
	 */
	public static String locationParam(String location) {
		return locationParam(location, false);
	}

	/**
	 * Creates an URL parameter transporting the location after login to the next link invocation.
	 */
	public static String locationParamFirst(String location) {
		return locationParam(location, true);
	}
	
	public static String locationParam(String location, boolean first) {
		String locationParam;
		if (location != null) {
			locationParam = (first ? "?" : "&") + LoginServlet.LOCATION_ATTRIBUTE + "=" + URLEncoder.encode(location, StandardCharsets.UTF_8);
		} else {
			locationParam = "";
		}
		return locationParam;
	}

	/**
	 * The location after login transmitted with the given request, or the current page.
	 */
	public static String location(HttpServletRequest request) {
		return location(request, null);
	}
	
	/**
	 * The location after login transmitted with the given request, or the given default location, or the current page.
	 */
	public static String location(HttpServletRequest request, String defaultLocation) {
		String locationAttribute = (String) request.getAttribute(LoginServlet.LOCATION_ATTRIBUTE);
		if (locationAttribute != null) {
			return locationAttribute;
		}
		
		String locationParam = (String) request.getParameter(LoginServlet.LOCATION_ATTRIBUTE);
		if (locationParam != null) {
			return locationParam;
		}
		
		if (defaultLocation != null) {
			return defaultLocation;
		}
		
		return ServletUtil.currentPage(request).substring(request.getContextPath().length());
	}

	/**
	 * Moves a location parameter to a request attribute.
	 */
	public static void forwardLocation(HttpServletRequest request) {
		String location = location(request);
		if (location != null) {
			request.setAttribute(LOCATION_ATTRIBUTE, location);
		}
	}

}
