/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import org.pac4j.jee.filter.CallbackFilter;

import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;

/**
 * {@link CallbackFilter} whose <code>defaultUrl</code> is given relative to the context path, so
 * the deployment descriptor does not depend on the deployment.
 */
public class PBCallbackFilter extends CallbackFilter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		super.init(filterConfig);
		setDefaultUrl(ServletUtil.withContextPath(filterConfig.getServletContext().getContextPath(), getDefaultUrl()));
	}

}
