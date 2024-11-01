/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
	"/support-banktransfer.jsp",
})
public class FormLoginFilter extends LoginFilter {

	private static final Logger LOG = LoggerFactory.getLogger(FormLoginFilter.class);
	
	@Override
	protected void requestLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
		String originalLocation = originalLocation(request);
		LOG.info("Requesting login for resource: " + originalLocation);

		response.sendRedirect(request.getContextPath() + LoginServlet.PATH + LoginServlet.locationParam(originalLocation, true));
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
