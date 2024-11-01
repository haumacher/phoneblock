/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import de.haumacher.phoneblock.ab.CreateABServlet;
import de.haumacher.phoneblock.ab.ListABServlet;
import de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse;
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
