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
import org.apache.ibatis.exceptions.PersistenceException;
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

	private String _dialPrefix;

	/** 
	 * Creates a {@link SearchOperation}.
	 */
	public SearchOperation(SchedulerService scheduler, IndexUpdateService indexer, List<AbstractMetaSearch> plugins, PhoneNumer number, String dialPrefix) {
		_scheduler = scheduler;
		_indexer = indexer;
		_plugins = plugins;
		_number = number;
		_dialPrefix = dialPrefix;
	}

	/** 
	 * Performs the search and updates the local DB.
	 *
	 * <p>
	 * The search runs in three steps, so that no transaction stays open while the external
	 * sites are queried (which can take several seconds): a short transaction claims the
	 * search by updating the <code>LASTMETA</code> timestamp, the meta search runs without
	 * any transaction, and a second short transaction stores the results. Keeping the row
	 * lock across the meta search let concurrent requests for the same number fail with a
	 * lock timeout.
	 * </p>
	 */
	public SearchOperation search() {
		DB db = DBService.getInstance();
		String phoneId = NumberAnalyzer.getPhoneId(_number);

		try (SqlSession session = db.openSession()) {
			SpamReports mapper = session.getMapper(SpamReports.class);
			_comments = new ArrayList<>(mapper.getAnyComments(phoneId));
		}

		_searchPerformed = claimSearch(db, phoneId);

		List<UserComment> newComments;
		if (_searchPerformed) {
			LOG.info("Performing meta search for: " + _number);
			newComments = doSearch();
		} else {
			LOG.info("Skipping search, still up-to-date: " + _number);
			newComments = Collections.emptyList();
		}

		if (newComments.isEmpty() && _votes == 0) {
			return this;
		}

		boolean indexUpdated = false;
		try (SqlSession session = db.openSession()) {
			SpamReports mapper = session.getMapper(SpamReports.class);

			Map<String, UserComment> indexedComments = index();

			int commentCnt = 0;
			for (UserComment comment : newComments) {
				if (!indexedComments.containsKey(comment.getComment())) {
					comment.setId(UUID.randomUUID().toString());
					_comments.add(comment);

					// Meta-search comments count as votes; ping the index only when
					// one of them pushes the number across the spam threshold.
					indexUpdated |= db.addRating(mapper, _number, _dialPrefix, comment, true);
					commentCnt++;
				}
			}
			if (_searchPerformed) {
				LOG.info("Found " + commentCnt + " new comments: " + _number);
			}

			if (_votes != 0) {
				indexUpdated |= db.processVotes(mapper, _number, _dialPrefix, _votes, _lastVote);
			}

			session.commit();
		}

		if (indexUpdated) {
			_indexer.publishUpdate(_number);
		}
		return this;
	}

	/**
	 * Claims the meta search for the given number in a transaction of its own.
	 *
	 * @return Whether this operation should perform the search. <code>false</code>, if the
	 *         last search is recent, or a concurrent request already claimed it.
	 */
	private boolean claimSearch(DB db, String phoneId) {
		try (SqlSession session = db.openSession()) {
			boolean doSearch = shouldSearch(session.getMapper(SpamReports.class), phoneId);
			session.commit();
			return doSearch;
		} catch (PersistenceException ex) {
			// Another transaction holds the row, e.g. a concurrent search storing its results.
			// The page can be rendered from the local data, the search is retried later.
			LOG.warn("Skipping meta search, number is locked: " + _number + " (" + ex.getCause() + ")");
			return false;
		}
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
	private boolean shouldSearch(SpamReports db, String phoneId) {
		long now = System.currentTimeMillis();
		Long lastMetaSearch = db.getLastMetaSearch(phoneId);
		if (lastMetaSearch == null) {
			// Use MERGE to atomically insert or update, avoiding race conditions
			// when multiple requests search for the same number simultaneously.
			db.mergeLastMetaSearch(phoneId, now);
			return true;
		} else if (lastMetaSearch.longValue() < now - SearchServlet.ONE_MONTH) {
			// Conditional update: of two concurrent requests, only the first one wins.
			return db.claimMetaSearch(phoneId, now, now - SearchServlet.ONE_MONTH) > 0;
		} else {
			return false;
		}
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
