/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
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
