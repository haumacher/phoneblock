/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import de.haumacher.phoneblock.callreport.CallReportServlet;
import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.db.DBService;

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
			Object userName = session.getAttribute(AUTHENTICATED_USER_ATTR);
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
				req.setAttribute(AUTHENTICATED_USER_ATTR, userName);
				req.getSession().setAttribute(AUTHENTICATED_USER_ATTR, userName);
				chain.doFilter(request, response);
				return;
			}
		}

		resp.setHeader("WWW-Authenticate", "Basic realm=\"PhoneBlock\", charset=\"UTF-8\"");
		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	}

	@Override
	public void destroy() {
		// Ignore.
	}

}
