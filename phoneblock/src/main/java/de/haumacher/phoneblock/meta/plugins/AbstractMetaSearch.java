/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.meta.plugins;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;

import de.haumacher.phoneblock.crawl.FetchService;
import de.haumacher.phoneblock.db.model.UserComment;

/**
 * Algorithm retrieving user comments for phone numbers somewhere in the web. 
 */
public abstract class AbstractMetaSearch {

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
	
	protected final Document load(String url) throws IOException {
		return _fetcher.fetch(new URL(url));
	}

	/** 
	 * Retrieves user comments for the given phone number.
	 */
	public abstract List<UserComment> fetchComments(String phone);
	

	/** 
	 * Utility to log the problem and return an empty list.
	 */
	protected final List<UserComment> notFound(Logger log, String phone, IOException ex) {
		log.info("Not found: " + phone + ": " + ex.getClass().getName() + ": " + ex.getMessage());
		return Collections.emptyList();
	}

}
