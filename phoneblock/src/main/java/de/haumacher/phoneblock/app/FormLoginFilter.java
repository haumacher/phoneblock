/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.settings.AuthToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
		LoginServlet.requestLogin(request, response);
	}

	@Override
	protected boolean checkTokenAuthorization(HttpServletRequest request, AuthToken authorization) {
		return authorization.isAccessLogin();
	}

}
