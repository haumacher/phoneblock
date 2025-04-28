/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.meta;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.UserComment;
import de.haumacher.phoneblock.crawl.FetchService;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.meta.plugins.AbstractMetaSearch;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Command line tool to start a meta search.
 */
public class MetaSearchService implements ServletContextListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(MetaSearchService.class);

	private static final long MIN_DELAY = TimeUnit.MINUTES.toMillis(7);

	private static final int JITTER = (int) TimeUnit.MINUTES.toMillis(3);

	private static MetaSearchService _instance;
	
	private IndexUpdateService _indexer;

	private SchedulerService _scheduler;
	private List<AbstractMetaSearch> _plugins;
	private FetchService _fetcher;
	
	private long _lastSearch = System.currentTimeMillis() - MIN_DELAY;
	
	private final ConcurrentLinkedQueue<Supplier<Boolean>> _jobs = new ConcurrentLinkedQueue<>();

	private ScheduledFuture<?> _heartBeat;

	/** 
	 * Creates a {@link MetaSearchService}.
	 */
	public MetaSearchService(SchedulerService scheduler, FetchService fetcher, IndexUpdateService indexer) {
		_scheduler = scheduler;
		_fetcher = fetcher;
		_indexer = indexer;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting meta search service.");
		_plugins = loadPlugins();
		
		_instance = this;
		
		_heartBeat = _scheduler.scheduler().scheduleWithFixedDelay(this::heartBeat, 1, 10, TimeUnit.MINUTES);
	}
	
	private void heartBeat() {
		LOG.info("MetaSearchService alive.");
		
		synchronized (this) {
			if (_task == null || _task.isDone()) {
				reSchedule(System.currentTimeMillis());
			}
		}
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
		
		if (_heartBeat != null) {
			_heartBeat.cancel(false);
			_heartBeat = null;
		}
		
		_plugins = null;
		if (_instance == this) {
			_instance = null;
		}
	}

	/**
	 * Look up user comments for the given phone number.
	 * 
	 * @param bot
	 *        Whether a bot requested the info.
	 */
	public List<UserComment> fetchComments(PhoneNumer phoneId, String dialPrefix) {
		return createSearch(phoneId, dialPrefix).search().getComments();
	}

	/**
	 * Schedules a meta search for a given phone number.
	 */
	public void scheduleMetaSearch(PhoneNumer phoneId) {
		_jobs.add(() -> createSearch(phoneId, null).search().searchPerformed());
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
	public void scheduleMetaSearch(int votes, long lastVote, PhoneNumer phoneId) {
		_jobs.add(() -> createSearch(phoneId, null).addVotes(votes, lastVote).search().searchPerformed());
	}

	private void reSchedule(long now) {
		long delay = Math.max(0, _lastSearch + MIN_DELAY - now);
		if (delay > 0) {
			delay += _rnd.nextInt(JITTER);
		}
		
		LOG.info("Scheduling next meta search in " + Duration.of(delay, ChronoUnit.MILLIS) + ", queue size is: " + _jobs.size());
		
		_task = _scheduler.scheduler().schedule(this::performSearch, delay, TimeUnit.MILLISECONDS);
	}

	private void performSearch() {
		Supplier<Boolean> job;
		long now;

		boolean searchPerformed;
		do {
			synchronized (this) {
				job = _jobs.poll();
				if (job == null) {
					return;
				}
				
				now = System.currentTimeMillis();
				_lastSearch = now;
			}
			
			searchPerformed = job.get();
		} while (!searchPerformed);

		synchronized (this) {
			if (!_jobs.isEmpty()) {
				reSchedule(now);
			}
		}
	}

	/** 
	 * Creates a search for the given phone number.
	 */
	private SearchOperation createSearch(PhoneNumer number, String dialPrefix) {
		return new SearchOperation(_scheduler, _indexer, _plugins, number, dialPrefix);
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
		MetaSearchService searchService = new MetaSearchService(scheduler, new FetchService(), IndexUpdateService.NONE);
		scheduler.contextInitialized(null);
		searchService.contextInitialized(null);
		String phoneText = args[0];
		PhoneNumer number = NumberAnalyzer.parsePhoneNumber(phoneText);
		if (number == null) {
			System.err.println("Invalid number: " + phoneText);
			return;
		}
		List<UserComment> comments = searchService.createSearch(number, NumberAnalyzer.GERMAN_DIAL_PREFIX).search().getComments();
		for (var comment : comments) {
			System.out.println(comment);
		}
		searchService.contextDestroyed(null);
		scheduler.contextDestroyed(null);
	}

}
