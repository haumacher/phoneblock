/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link HttpServlet} that redirects to external pages.
 */
@WebServlet(urlPatterns = "/link/*")
public class ExternalLinkServlet extends HttpServlet {
	
	private static final Logger LOG = LoggerFactory.getLogger(ExternalLinkServlet.class);

	private Properties _properties;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		_properties = new Properties();
		try {
			_properties.load(ExternalLinkServlet.class.getResourceAsStream("/link.properties"));
			LOG.info("Loaded " + _properties.size() + " external links.");
		} catch (IOException ex) {
			LOG.error("Failed to load external link properties.", ex);
		}
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || !pathInfo.startsWith("/")) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		String linkName = pathInfo.substring(1);
		String link = _properties.getProperty(linkName);
		if (link == null) {
			LOG.warn("Unknown link requested: " + linkName);
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		resp.sendRedirect(link);
	}

}
