/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.meta;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.SearchServlet;
import de.haumacher.phoneblock.crawl.FetchService;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.model.UserComment;
import de.haumacher.phoneblock.meta.plugins.AbstractMetaSearch;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Command line tool to start a meta search.
 */
public class MetaSearchService implements ServletContextListener, Runnable {
	
	private static final Logger LOG = LoggerFactory.getLogger(MetaSearchService.class);

	private static final long MIN_DELAY = TimeUnit.MINUTES.toMillis(7);

	private static final int JITTER = (int) TimeUnit.MINUTES.toMillis(3);

	private static MetaSearchService _instance;
	
	private SchedulerService _scheduler;
	private List<AbstractMetaSearch> _plugins;
	private FetchService _fetcher;
	
	private long _lastSearch = System.currentTimeMillis() - MIN_DELAY;
	
	private final ConcurrentLinkedQueue<Runnable> _jobs = new ConcurrentLinkedQueue<>();

	/** 
	 * Creates a {@link MetaSearchService}.
	 * @param fetcher 
	 */
	public MetaSearchService(SchedulerService scheduler, FetchService fetcher) {
		_scheduler = scheduler;
		_fetcher = fetcher;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting meta search service.");
		_plugins = loadPlugins();
		
		_instance = this;
	}

	Class<?>[] PLUGINS = {
		de.haumacher.phoneblock.meta.plugins.MetaAnruferBewertung.class,
		de.haumacher.phoneblock.meta.plugins.MetaCleverdialer.class,
		de.haumacher.phoneblock.meta.plugins.MetaTellows.class,
		de.haumacher.phoneblock.meta.plugins.MetaWemgehoert.class,
		de.haumacher.phoneblock.meta.plugins.MetaWerruft.class,
		de.haumacher.phoneblock.meta.plugins.MetaWerruftAn.class,
		de.haumacher.phoneblock.meta.plugins.MetaRueckwaertssuche.class,
	};

	private ScheduledFuture<?> _task;

	private Random _rnd = new Random();
	
	private List<AbstractMetaSearch> loadPlugins() {
		List<AbstractMetaSearch> result = new ArrayList<>();
		for (Class<?> impl : PLUGINS) {
			try {
				AbstractMetaSearch searcher = (AbstractMetaSearch) impl.getConstructor().newInstance();
				result.add(searcher.setFetcher(_fetcher));
				LOG.info("Found meta search plugin: " + searcher.getClass());
			} catch (Throwable ex) {
				LOG.error("Cannot instantiate search plugin: " + impl.getName());
			}
		}
		return result;
	}
	
	// Seems not to work in Tomcat, why?
	private List<AbstractMetaSearch> loadPlugins2() {
		List<AbstractMetaSearch> result = new ArrayList<>();
		ServiceLoader<AbstractMetaSearch> loader = ServiceLoader.load(AbstractMetaSearch.class);
		for (AbstractMetaSearch searcher : loader) {
			result.add(searcher.setFetcher(_fetcher));
			LOG.info("Found meta search plugin: " + searcher.getClass());
		}
		return result;
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		LOG.info("Stopping meta search service.");
		_plugins = null;
		if (_instance == this) {
			_instance = null;
		}
	}

	/**
	 * Main for debugging only.
	 */
	public List<UserComment> search(String phone) {
		Stream<Pair<AbstractMetaSearch, Future<List<UserComment>>>> s1 = _plugins.stream().map(query -> Pair.of(query, _scheduler.executor().submit(() -> {
			try {
				return query.fetchComments(phone);
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
	 * Look up user comments for the given phone number.
	 */
	public List<UserComment> fetchComments(String phoneId) {
		long lastVote = Long.MIN_VALUE;
		int votes = 0;
		
		return doMetaSearch(votes, lastVote, phoneId);
	}
	
	/**
	 * Schedules a meta search for a given phone number.
	 *
	 * @param votes
	 *        The votes to add anyways.
	 * @param lastVote
	 *        The time when the given votes were added ({@link Long#MIN_VALUE} if no votes are
	 *        given).
	 * @param phoneId
	 *        The phone number to search comments and votes for.
	 */
	public void scheduleMetaSearch(int votes, long lastVote, String phoneId) {
		_jobs.add(() -> doMetaSearch(votes, lastVote, phoneId));

		synchronized (this) {
			reSchedule();
		}
	}

	private void reSchedule() {
		if (_task == null || _task.isDone()) {
			reSchedule(System.currentTimeMillis());
		}
	}

	@Override
	public void run() {
		Runnable job;
		long now;

		synchronized (this) {
			job = _jobs.poll();
			if (job == null) {
				return;
			}
			
			now = System.currentTimeMillis();
			_lastSearch = now;
		}
		
		job.run();

		synchronized (this) {
			if (!_jobs.isEmpty()) {
				reSchedule(now);
			}
		}
	}

	private void reSchedule(long now) {
		long delay = Math.max(0, _lastSearch + MIN_DELAY - now);
		if (delay > 0) {
			delay += _rnd.nextInt(JITTER);
		}
		
		LOG.info("Scheduling next meta search in " + Duration.of(delay, ChronoUnit.MILLIS) + ", queue size is: " + _jobs.size());
		
		_task = _scheduler.executor().schedule(this, delay, TimeUnit.MILLISECONDS);
	}

	/**
	 * Perform a meta search and store found comments and votes into the local DB.
	 *
	 * @param votes
	 *        The votes to add to the given number (from some other source).
	 * @param lastVote
	 *        The time of the given votes ({@link Long#MIN_VALUE}, if no votes are given).
	 * @param phoneId
	 *        The phone number to search for.
	 * @return All user comments that exist for the given phone number.
	 */
	private List<UserComment> doMetaSearch(int votes, long lastVote, String phoneId) {
		List<UserComment> comments;
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			SpamReports mapper = session.getMapper(SpamReports.class);

			comments = new ArrayList<>(mapper.getComments(phoneId));

			boolean commit = false;
			long now = System.currentTimeMillis();
			Long lastMetaSearch = mapper.getLastMetaSearch(phoneId);
			boolean doSearch;
			if (lastMetaSearch == null) {
				mapper.insertLastMetaSearch(phoneId, now);
				commit = true;
				doSearch = true;
			} else if (lastMetaSearch.longValue() < now - SearchServlet.ONE_MONTH) {
				mapper.setLastMetaSearch(phoneId, now);
				doSearch = true;
				commit = true;
			} else {
				doSearch = false;
			}
			
			boolean updatedRequired = false;
			if (doSearch) {
				LOG.info("Performing meta search for: " + phoneId);

				Map<String, UserComment> indexedComments = comments.stream().collect(Collectors.toMap(c -> c.getComment(), c -> c));

				int commentCnt = 0;
				List<UserComment> newComments = search(phoneId);
				for (UserComment comment : newComments) {
					if (!indexedComments.containsKey(comment.getComment())) {
						comment.setId(UUID.randomUUID().toString());
						comments.add(comment);
						
						updatedRequired |= db.addRating(mapper, comment);
						commentCnt++;
						
						commit = true;
					}
				}
				LOG.info("Found " + commentCnt + " new comments: " + phoneId);
			} else {
				LOG.info("Skipping search, still up-to-date: " + phoneId);
			}
			
			if (votes != 0) {
				updatedRequired |= db.processVotes(mapper, phoneId, votes, lastVote);
				commit = true;
			}
			
			if (commit) {
				session.commit();
			}
			
			if (updatedRequired) {
				db.publishUpdate(phoneId);
			}
		}
		return comments;
	}

	/** 
	 * This service.
	 */
	public static MetaSearchService getInstance() {
		return _instance;
	}

	/**
	 * For debugging only.
	 */
	public static void main(String[] args) {
		SchedulerService scheduler = new SchedulerService();
		MetaSearchService searchService = new MetaSearchService(scheduler, new FetchService());
		scheduler.contextInitialized(null);
		searchService.contextInitialized(null);
		List<UserComment> comments = searchService.search(args[0]);
		for (var comment : comments) {
			System.out.println(comment);
		}
		searchService.contextDestroyed(null);
		scheduler.contextDestroyed(null);
	}

}
