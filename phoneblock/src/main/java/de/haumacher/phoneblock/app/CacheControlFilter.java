/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link Filter} setting reasonable cache control headers.
 */
@WebFilter(urlPatterns = "/*", dispatcherTypes = DispatcherType.REQUEST)
public class CacheControlFilter implements Filter {

	private static final Logger LOG = LoggerFactory.getLogger(CacheControlFilter.class);

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		LOG.info("Loaded cache filter.");
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		if (noCache((HttpServletRequest) request)) {
			httpResponse.setHeader("Cache-Control", "no-cache");
		} else {
			httpResponse.setHeader("Cache-Control", "max-age=72000");
		}

		chain.doFilter(request, response);
	}

	private boolean noCache(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.endsWith(".jsp") || path.endsWith("/") || path.lastIndexOf('.') < 0;
	}

}
