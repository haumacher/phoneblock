/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.ab.CreateABServlet;
import de.haumacher.phoneblock.ab.ListABServlet;
import de.haumacher.phoneblock.app.api.AccountManagementServlet;
import de.haumacher.phoneblock.app.api.BlocklistServlet;
import de.haumacher.phoneblock.app.api.PersonalizationServlet;
import de.haumacher.phoneblock.app.api.RateServlet;
import de.haumacher.phoneblock.app.api.SearchApiServlet;
import de.haumacher.phoneblock.app.api.SpamCheckServlet;
import de.haumacher.phoneblock.app.api.TestConnectServlet;
import de.haumacher.phoneblock.callreport.CallReportServlet;
import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter enforcing HTTP login.
 * 
 * @see FormLoginFilter
 */
@WebFilter(urlPatterns = {
	CreateABServlet.PATH,
	ListABServlet.PATH,
	AccountManagementServlet.PATH,
	PersonalizationServlet.BLACKLIST_PATTERN,
	PersonalizationServlet.WHITELIST_PATTERN,
	BlocklistServlet.PATH,
	SpamCheckServlet.PATH,
	TestConnectServlet.PATH,
	RateServlet.PATH,
	CallReportServlet.URL_PATTERN,
	SearchApiServlet.PATTERN,
	CardDavServlet.URL_PATTERN,
})
public class BasicLoginFilter extends LoginFilter {

	private static final Logger LOG = LoggerFactory.getLogger(BasicLoginFilter.class);

	private static final String[] RAW_PATHS = BasicLoginFilter.class.getAnnotation(WebFilter.class).urlPatterns();
	private static final Set<String> PATHS = Arrays.stream(RAW_PATHS).map(p -> p.endsWith("/*") ? p.substring(0, p.length() - 2) : p).collect(Collectors.toSet());
	
	public static boolean matches(String servletPath) {
		return PATHS.contains(servletPath);
	}
	
	@Override
	protected void requestLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException {
		LOG.debug("Requesting authentication for: {}", request.getServletPath());
		ServletUtil.sendAuthenticationRequest(response);
	}
	
	@Override
	protected boolean allowSessionAuth(HttpServletRequest request) {
		switch (request.getServletPath()) {
		case TestConnectServlet.PATH: 
			return false;
		default:
			return true;
		}
	}
	
	@Override
	protected boolean allowCookieAuth(HttpServletRequest request) {
		switch (request.getServletPath()) {
		case TestConnectServlet.PATH: 
			return false;
		default:
			return true;
		}
	}
	
	@Override
	protected boolean allowBasicAuth(HttpServletRequest request) {
		switch (request.getServletPath()) {
		case TestConnectServlet.PATH: 
			return false;
		default:
			return true;
		}
	}
	
	@Override
	protected boolean checkTokenAuthorization(HttpServletRequest request, AuthToken authorization) {
		switch (request.getServletPath()) {
		case BlocklistServlet.PATH:
			return authorization.isAccessDownload();
		case RateServlet.PATH:
		case CallReportServlet.URL_PATTERN:
		case PersonalizationServlet.BLACKLIST_PATH:
		case PersonalizationServlet.WHITELIST_PATH:
		case AccountManagementServlet.PATH:
			return authorization.isAccessRate();
		case CreateABServlet.PATH:
		case ListABServlet.PATH:
		case SearchApiServlet.PREFIX:
		case SpamCheckServlet.PATH:
		case TestConnectServlet.PATH:
			return authorization.isAccessQuery();
		case CardDavServlet.DIR_NAME:
			return authorization.isAccessCarddav();
		default:
			LOG.warn("Requesting CardDAV permission for unknown resource: {} - {}", request.getServletPath(), request.getPathInfo());
			
			return authorization.isAccessCarddav();
		}
	}
	
	@Override
	protected void setUser(HttpServletRequest req, AuthToken authorization) {
		setRequestUser(req, authorization);
	}

}
