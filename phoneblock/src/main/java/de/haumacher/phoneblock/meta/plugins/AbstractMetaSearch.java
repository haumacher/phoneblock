/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.meta.plugins;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.jsoup.nodes.Document;

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
	
	protected final Document load(String url) throws MalformedURLException, IOException {
		return _fetcher.fetch(new URL(url));
	}

	/** 
	 * Retrieves user comments for the given phone number.
	 */
	public abstract List<UserComment> fetchComments(String phone) throws Throwable;

}
