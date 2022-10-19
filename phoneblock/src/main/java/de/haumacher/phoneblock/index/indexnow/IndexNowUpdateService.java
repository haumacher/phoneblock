/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.index.indexnow;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.util.ConnectionUtil;

/**
 * Service pushing updated URLs to an "indexnow" API.
 */
public class IndexNowUpdateService implements IndexUpdateService {

	private static final Logger LOG = LoggerFactory.getLogger(IndexUpdateService.class);

	private String _apiKey;
	private String _contextPath;
	private boolean _active;
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		_apiKey = lookupApiKey();
		_active = _apiKey != null;
		if (_active) {
			_contextPath = sce.getServletContext().getContextPath();
			LOG.info("Activated indexnow service.");
		} else {
			LOG.warn("No API key for indexnow, deactivating updates.");
		}
	}

	private String lookupApiKey() {
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			
			try {
				return (String) envCtx.lookup("indexnow/key");
			} catch (NamingException ex) {
				LOG.info("No API key for indexnow: " + ex.getMessage());
			}
		} catch (NamingException ex) {
			LOG.info("Not using JNDI configuration: " + ex.getMessage());
		}
		return null;
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		// Ignore.
	}
	
	@Override
	public void publishUpdate(String path) {
		if (_active) {
			doPublishUpdate(path);
		}
	}
	
	private void doPublishUpdate(String path) {
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		String url = "https://phoneblock.haumacher.de" + _contextPath + path;
		try {
			URL indexnowUrl = new URL("https://www.bing.com/indexnow?url=" + URLEncoder.encode(url, "utf-8") + "&key=" + _apiKey);
			HttpURLConnection connection = (HttpURLConnection) indexnowUrl.openConnection();
			connection.connect();
			int code = connection.getResponseCode();
			if (code != HttpURLConnection.HTTP_OK) {
				LOG.error("Failed to send URL update of '" + url + "' (status " + code + "): " + ConnectionUtil.readText(connection));
			} else {
				LOG.info("Updated URL in indexnow: " + url);
			}
		} catch (IOException ex) {
			LOG.error("Failed to send URL update of '" + url + "'.", ex);
		}
	}

}
