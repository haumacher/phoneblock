/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import de.haumacher.phoneblock.callreport.CallReportServlet;
import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.ServletUtil;

/**
 * Filter doing basic authentication.
 */
@WebFilter(urlPatterns = {
	CardDavServlet.URL_PATTERN,
	CallReportServlet.URL_PATTERN,
})
public class LoginFilter implements Filter {

	public static final String AUTHENTICATED_USER_ATTR = "authenticated-user";

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// Ignore.
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;
		
		// Short-cut to prevent authenticating every request.
		HttpSession session = req.getSession(false);
		if (session != null) {
			String userName = getAuthenticatedUser(session);
			if (userName != null) {
				req.setAttribute(AUTHENTICATED_USER_ATTR, userName);
				chain.doFilter(request, response);
				return;
			}
		}

		String authHeader = req.getHeader("Authorization");
		if (authHeader != null) {
			String userName = DBService.getInstance().basicAuth(authHeader);
			if (userName != null) {
				setAuthenticatedUser(req, userName);
				chain.doFilter(request, response);
				return;
			}
		}

		ServletUtil.sendAuthenticationRequest(resp);
	}

	/** 
	 * The authenticated user of the given session.
	 */
	public static String getAuthenticatedUser(HttpSession session) {
		if (session == null) {
			return null;
		}
		return (String) session.getAttribute(AUTHENTICATED_USER_ATTR);
	}

	/** 
	 * Adds the given user name to the request and session.
	 */
	public static final void setAuthenticatedUser(HttpServletRequest req, String userName) {
		req.setAttribute(AUTHENTICATED_USER_ATTR, userName);
		req.getSession().setAttribute(AUTHENTICATED_USER_ATTR, userName);
	}

	@Override
	public void destroy() {
		// Ignore.
	}

}
