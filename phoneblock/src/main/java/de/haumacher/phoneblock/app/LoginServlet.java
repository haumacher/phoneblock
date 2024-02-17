/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DBService;

/**
 * Servlet displaying information about a telephone number in the DB.
 */
@WebServlet(urlPatterns = LoginServlet.PATH)
public class LoginServlet extends HttpServlet {
	
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
		req.getRequestDispatcher("/login.jsp").forward(req, resp);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = req.getParameter("userName");
		String password = req.getParameter("password");
		
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
		
		String authenticatedUser = DBService.getInstance().login(userName, password);
		if (authenticatedUser == null) {
			LOG.warn("Login failed for user: " + userName);
			sendFailure(req, resp);
			return;
		}
		
		LoginFilter.setAuthenticatedUser(req, authenticatedUser);
		
		String location = location(req);
		if (location == null) {
			resp.sendRedirect(req.getContextPath() + SettingsServlet.PATH);
		} else {
			resp.sendRedirect(req.getContextPath() + location);
		}
	}

	/**
	 * Redirects the client to the login page.
	 */
	public static void sendFailure(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setAttribute("error", "Anmeldung fehlgeschlagen.");
		req.getRequestDispatcher("/login.jsp").forward(req, resp);
	}

	/**
	 * Encodes URL parameter transporting the location after login to the next link invocation.
	 */
	public static String locationParam(HttpServletRequest request) throws UnsupportedEncodingException {
		return locationParam(request, false);
	}
	
	/**
	 * Encodes URL parameter transporting the location after login to the next link invocation.
	 */
	public static String locationParamFirst(HttpServletRequest request) throws UnsupportedEncodingException {
		return locationParam(request, true);
	}
	
	/**
	 * Encodes URL parameter transporting the location after login to the next link invocation.
	 */
	private static String locationParam(HttpServletRequest request, boolean first) throws UnsupportedEncodingException {
		String location = location(request);
		String locationParam;
		if (location != null) {
			locationParam = (first ? "?" : "&") + LoginServlet.LOCATION_ATTRIBUTE + "=" + URLEncoder.encode(location, "UTF-8");
		} else {
			locationParam = "";
		}
		return locationParam;
	}

	/**
	 * The location after login transmitted with the given request.
	 */
	public static String location(HttpServletRequest request) {
		String location = (String) request.getAttribute(LoginServlet.LOCATION_ATTRIBUTE);
		if (location == null) {
			location = (String) request.getParameter(LoginServlet.LOCATION_ATTRIBUTE);
		}
		return location;
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
