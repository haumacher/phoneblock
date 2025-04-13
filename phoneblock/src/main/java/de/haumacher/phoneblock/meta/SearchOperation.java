/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.meta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.SearchServlet;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.UserComment;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.meta.plugins.AbstractMetaSearch;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * A meta web search operation for a phone number.
 */
public class SearchOperation {

	private static final Logger LOG = LoggerFactory.getLogger(SearchOperation.class);

	private int _votes;
	private long _lastVote;
	private PhoneNumer _number;

	private List<AbstractMetaSearch> _plugins;

	private SchedulerService _scheduler;
	
	private IndexUpdateService _indexer;

	private List<UserComment> _comments;

	private boolean _searchPerformed;

	/** 
	 * Creates a {@link SearchOperation}.
	 */
	public SearchOperation(SchedulerService scheduler, IndexUpdateService indexer, List<AbstractMetaSearch> plugins, PhoneNumer number) {
		_scheduler = scheduler;
		_indexer = indexer;
		_plugins = plugins;
		_number = number;
	}

	/** 
	 * Performs the search and updates the local DB.
	 */
	public SearchOperation search() {
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			SpamReports mapper = session.getMapper(SpamReports.class);

			String phoneId = NumberAnalyzer.getPhoneId(_number);
			
			_comments = new ArrayList<>(mapper.getAnyComments(phoneId));
			{
				_searchPerformed = shouldSearch(mapper, _number);
				
				boolean indexUpdated = false;
				if (_searchPerformed) {
					LOG.info("Performing meta search for: " + _number);
	
					Map<String, UserComment> indexedComments = index();
	
					int commentCnt = 0;
					List<UserComment> newComments = doSearch();
					for (UserComment comment : newComments) {
						if (!indexedComments.containsKey(comment.getComment())) {
							comment.setId(UUID.randomUUID().toString());
							_comments.add(comment);
							
							db.addRating(mapper, _number, comment, true);
							commentCnt++;
							indexUpdated = true;
						}
					}
					LOG.info("Found " + commentCnt + " new comments: " + _number);
				} else {
					LOG.info("Skipping search, still up-to-date: " + _number);
				}
				
				boolean commit = _searchPerformed;
				
				if (_votes != 0) {
					indexUpdated |= db.processVotes(mapper, _number, _votes, _lastVote);
					commit = true;
				}
				
				if (commit) {
					session.commit();
				}
				
				if (indexUpdated) {
					_indexer.publishUpdate(_number);
				}
			}
		}
		return this;
	}

	private Map<String, UserComment> index() {
		HashMap<String, UserComment> result = new HashMap<>();
		for (UserComment c : _comments) {
			result.put(c.getComment(), c);
		}
		return result;
	}

	/**
	 * Whether the last {@link #search()} actually did update the stored comments. 
	 */
	public boolean searchPerformed() {
		return _searchPerformed;
	}

	/**
	 * Check whether an update of the meta-search results is required.
	 * 
	 * @return Whether a search should be performed.
	 */
	private boolean shouldSearch(SpamReports db, PhoneNumer number) {
		String phoneId = NumberAnalyzer.getPhoneId(number);
		
		long now = System.currentTimeMillis();
		Long lastMetaSearch = db.getLastMetaSearch(phoneId);
		boolean doSearch;
		if (lastMetaSearch == null) {
			db.insertLastMetaSearch(phoneId, NumberAnalyzer.getPhoneHash(number), now);
			doSearch = true;
		} else if (lastMetaSearch.longValue() < now - SearchServlet.ONE_MONTH) {
			db.setLastMetaSearch(phoneId, now);
			doSearch = true;
		} else {
			doSearch = false;
		}
		return doSearch;
	}

	/**
	 * Main for debugging only.
	 */
	private List<UserComment> doSearch() {
		String phoneId = NumberAnalyzer.getPhoneId(_number);
		
		Stream<Pair<AbstractMetaSearch, Future<List<UserComment>>>> s1 = _plugins.stream().map(query -> Pair.of(query, _scheduler.executor().submit(() -> {
			try {
				return query.fetchComments(phoneId);
			} catch (Throwable ex) {
				return Collections.emptyList();
			}
		})));
		
		List<UserComment> comments = s1.flatMap(f -> {
			try {
				return f.getRight().get(5, TimeUnit.SECONDS).stream();
			} catch (InterruptedException | ExecutionException ex) {
				ex.printStackTrace();
				return Collections.<UserComment>emptyList().stream();
			} catch (TimeoutException ex) {
				LOG.info("Timeout waiting for meta search: " + f.getLeft().getClass().getName());
				return Collections.<UserComment>emptyList().stream();
			}
		})
		.collect(Collectors.toList());
		
		return comments;
	}

	/** 
	 * All comments found in the local DB and during the meta search.
	 */
	public List<UserComment> getComments() {
		return _comments;
	}

	/** 
	 * Adds additional votes that should be stored in the local DB together with the search results.
	 */
	public SearchOperation addVotes(int votes, long lastVote) {
		_votes = votes;
		_lastVote = lastVote;
		return this;
	}
	
}
