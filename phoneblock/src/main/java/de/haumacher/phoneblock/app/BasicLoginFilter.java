/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.haumacher.phoneblock.callreport.CallReportServlet;
import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.util.ServletUtil;

/**
 * Filter requesting basic authentication.
 * 
 * @see FormLoginFilter
 */
@WebFilter(urlPatterns = {
	CardDavServlet.URL_PATTERN,
	CallReportServlet.URL_PATTERN,
})
public class BasicLoginFilter extends LoginFilter {

	@Override
	protected void requestLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException {
		ServletUtil.sendAuthenticationRequest(response);
	}

}
