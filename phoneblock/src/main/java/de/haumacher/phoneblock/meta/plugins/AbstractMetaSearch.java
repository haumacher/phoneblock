/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.meta.plugins;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.api.model.UserComment;
import de.haumacher.phoneblock.crawl.FetchBlockedException;
import de.haumacher.phoneblock.crawl.FetchService;

/**
 * Algorithm retrieving user comments for phone numbers somewhere in the web. 
 */
public abstract class AbstractMetaSearch {

	private static final Logger LOG = LoggerFactory.getLogger(MetaAnruferBewertung.class);

	private volatile long _nextPause = 5 * 60 * 1000;
	
	private volatile long _notBefore = 0;
	
	private FetchService _fetcher;

	/** 
	 * Creates a {@link AbstractMetaSearch}.
	 */
	public AbstractMetaSearch() {
		super();
	}
	
	/**
	 * Initializes the {@link FetchService}.
	 */
	public AbstractMetaSearch setFetcher(FetchService fetcher) {
		_fetcher = fetcher;
		return this;
	}
	
	protected final Document load(String url) throws IOException, FetchBlockedException {
		return _fetcher.fetch(new URL(url));
	}

	/** 
	 * Retrieves user comments for the given phone number.
	 */
	public final List<UserComment> fetchComments(String phone) {
		long notBefore = _notBefore;
		
		if (notBefore > 0) {
			if (System.currentTimeMillis() < notBefore) {
				LOG.info("Skipping search for " + phone + " from " + getService() + 
						" due to overload until " + new Date(_notBefore) + ".");
				return Collections.emptyList();
			}
		}
		
		try {
			List<UserComment> result = doFetchComments(phone);
			_notBefore = 0;
			return result;
		} catch (FetchBlockedException ex) {
			if (notBefore > 0) {
				// Exponential backoff, pause for longer.
				_nextPause = _nextPause * 3  / 2;
			}
			_notBefore = System.currentTimeMillis() + _nextPause;
			
			LOG.warn("Fetch from " + getService() + " was blocked, pausing for " + (_nextPause / 1000 / 60) + 
					" minutes until " + new Date(_notBefore) + ": " + ex.getCause().getMessage());
			
			return Collections.emptyList();
		}
	}
	
	/** 
	 * The name of the search service.
	 */
	protected abstract String getService();

	public abstract List<UserComment> doFetchComments(String phone) throws FetchBlockedException;
	

	/** 
	 * Utility to log the problem and return an empty list.
	 */
	protected final List<UserComment> notFound(Logger log, String phone, IOException ex) {
		log.info("Not found: " + phone + ": " + ex.getClass().getName() + ": " + ex.getMessage());
		return Collections.emptyList();
	}

}
