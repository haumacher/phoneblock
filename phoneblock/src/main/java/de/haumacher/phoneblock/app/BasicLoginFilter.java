/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.FilterChain;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import de.haumacher.phoneblock.ab.CreateABServlet;
import de.haumacher.phoneblock.ab.ListABServlet;
import de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse;
import de.haumacher.phoneblock.app.api.BlocklistServlet;
import de.haumacher.phoneblock.app.api.RateServlet;
import de.haumacher.phoneblock.app.api.SearchApiServlet;
import de.haumacher.phoneblock.callreport.CallReportServlet;
import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.util.ServletUtil;

/**
 * Filter enforcing HTTP login.
 * 
 * @see FormLoginFilter
 */
@WebFilter(urlPatterns = {
	CreateABServlet.PATH,
	ListABServlet.PATH,
	BlocklistServlet.PATH,
	RateServlet.PATH,
	CallReportServlet.URL_PATTERN,
	SearchApiServlet.PATTERN,
	CardDavServlet.URL_PATTERN,
})
public class BasicLoginFilter extends LoginFilter {

	private static final Logger LOG = LoggerFactory.getLogger(BasicLoginFilter.class);
	
	@Override
	protected void requestLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException {
		LOG.debug("Requesting authentication for: {}", request.getServletPath());
		ServletUtil.sendAuthenticationRequest(response);
	}
	
	@Override
	protected boolean checkTokenAuthorization(HttpServletRequest request, AuthToken authorization) {
		switch (request.getServletPath()) {
		case BlocklistServlet.PATH: 
			return authorization.isAccessDownload();
		case RateServlet.PATH: 
		case CallReportServlet.URL_PATTERN: 
			return authorization.isAccessRate();
		case CreateABServlet.PATH: 
		case ListABServlet.PATH: 
		case SearchApiServlet.PREFIX: 
			return authorization.isAccessQuery();
		case CardDavServlet.DIR_NAME: 
			return authorization.isAccessCarddav();
		default:
			LOG.warn("Requesting CardDAV permission for unknown resource: {} - {}", request.getServletPath(), request.getPathInfo());
			
			return authorization.isAccessCarddav();
		}
	}
	
	@Override
	protected void setUser(HttpServletRequest req, String userName) {
		setRequestUser(req, userName);
	}

}
