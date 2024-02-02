/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.util.ServletUtil;

/**
 * Filter requesting basic authentication.
 * 
 * @see BasicLoginFilter
 */
@WebFilter(urlPatterns = {
	"/ab",
	"/ab/index.html",
})
public class FormLoginFilter extends LoginFilter {

	private static final Logger LOG = LoggerFactory.getLogger(FormLoginFilter.class);
	
	@Override
	protected void requestLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
		String originalLocation = originalLocation(request);
		LOG.info("Requesting login for resource: " + originalLocation);
		request.setAttribute(LoginServlet.LOCATION_ATTRIBUTE, originalLocation);
		request.getRequestDispatcher(LoginServlet.PATH).forward(request, response);
	}

	private String originalLocation(HttpServletRequest request) {
		StringBuilder location = new StringBuilder();
		location.append(request.getServletPath());
		String pathInfo = request.getPathInfo();
		if (pathInfo != null) {
			location.append(pathInfo);
		}
		String query = request.getQueryString();
		if (query != null) {
			location.append('?');
			location.append(query);
		}
		return location.toString();
	}

}
