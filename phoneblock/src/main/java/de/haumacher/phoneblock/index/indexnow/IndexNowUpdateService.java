/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.index.indexnow;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.util.ConnectionUtil;
import jakarta.servlet.ServletContextEvent;

/**
 * Service pushing updated URLs to an "indexnow" API.
 */
public class IndexNowUpdateService implements IndexUpdateService {

	private static final Logger LOG = LoggerFactory.getLogger(IndexUpdateService.class);

	/** After this many consecutive failures, pause instead of logging every one. */
	private static final int MAX_FAILURES = 5;

	/** How long to stop submitting once indexnow is failing repeatedly. */
	private static final long BACKOFF_MS = 60 * 60 * 1000L;

	private String _apiKey;
	private String _contextPath;
	private boolean _active;

	private int _consecutiveFailures;

	/** While {@code now < this}, skip submissions: indexnow is failing repeatedly. */
	private volatile long _pausedUntil;

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
	public void publishPathUpdate(String path) {
		if (!_active) {
			return;
		}
		// When indexnow rejects (a bad/unverified key, or rate limiting) it does so
		// for every URL, flooding the log. Back off once it fails repeatedly.
		if (System.currentTimeMillis() < _pausedUntil) {
			return;
		}
		doPublishUpdate(path);
	}

	private void doPublishUpdate(String path) {
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		String url = "https://phoneblock.net" + _contextPath + path;
		try {
			URL indexnowUrl = new URL("https://www.bing.com/indexnow?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8) + "&key=" + _apiKey);
			HttpURLConnection connection = (HttpURLConnection) indexnowUrl.openConnection();
			connection.connect();
			int code = connection.getResponseCode();
			if (code != HttpURLConnection.HTTP_OK) {
				onFailure(url, "status " + code + ": " + ConnectionUtil.readText(connection));
			} else {
				_consecutiveFailures = 0;
				LOG.info("Updated URL in indexnow: " + url);
			}
		} catch (IOException ex) {
			onFailure(url, ex.getMessage());
		}
	}

	/**
	 * Records a failed submission, logging each one until {@link #MAX_FAILURES} in a
	 * row; from then on it pauses for {@link #BACKOFF_MS} and logs only once, so a
	 * persistently broken indexnow endpoint cannot flood the log.
	 */
	private void onFailure(String url, String detail) {
		_consecutiveFailures++;
		if (_consecutiveFailures >= MAX_FAILURES) {
			_pausedUntil = System.currentTimeMillis() + BACKOFF_MS;
			LOG.warn("Indexnow failing repeatedly, pausing updates for {} min. Last failure: {}",
				BACKOFF_MS / 60000, detail);
		} else {
			LOG.warn("Failed to send URL update of '" + url + "': " + detail);
		}
	}

}
