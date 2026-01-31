/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.analysis.NumberBlock;
import de.haumacher.phoneblock.analysis.NumberTree;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DBNumberInfo;
import de.haumacher.phoneblock.db.IDBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.UserSettings;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Cache for user address books that keep the data in memory between requests.
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class AddressBookCache implements ServletContextListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(AddressBookCache.class);

	private final Cache<String, AddressBookResource> _userCache = new Cache<>("user");

	private final Cache<ListType, List<NumberBlock>> _numberCache = new Cache<>("common");

	private static AddressBookCache _instance;

	private IDBService _db;
	
	public AddressBookCache(IDBService db) {
		_db = db;
	}
	
	/**
	 * The system-wide {@link AddressBookCache}.
	 */
	public static AddressBookCache getInstance() {
		return _instance;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		_instance = this;
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_instance == this) {
			_instance = null;
		}
	}
	
	/**
	 * Drops a cache entry for the given user.
	 */
	public void flushUserCache(String principal) {
		_userCache.flush(principal);
	}
	
	/**
	 * Looks up the block list resource for the given user.
	 *
	 * @param settings The user settings (from request attribute).
	 */
	public AddressBookResource lookupAddressBook(String rootUrl, String serverRoot, String resourcePath,
			String principal, UserSettings settings) {
		AddressBookResource cachedResult = _userCache.lookup(principal);
		if (cachedResult != null) {
			return cachedResult;
		}

		long now = System.currentTimeMillis();
		List<NumberBlock> phoneNumbers = loadNumbers(principal, now, settings);
		AddressBookResource addressBook = new AddressBookResource(rootUrl, serverRoot, resourcePath, principal, phoneNumbers);
		return _userCache.put(principal, addressBook);
	}

	List<NumberBlock> loadNumbers(String principal, long now, UserSettings settings) {
		List<NumberBlock> phoneNumbers;
		try (SqlSession session = _db.db().openSession()) {
			Users users = session.getMapper(Users.class);

			int minVotes = settings.getMinVotes();
			int maxLength = settings.getMaxLength();
			boolean wildcards = settings.isWildcards();
			String dialPrefix = settings.getDialPrefix();
			boolean national = settings.isNationalOnly();

			ListType listType = ListType.valueOf(dialPrefix, minVotes, maxLength, wildcards, national);
			phoneNumbers = loadNumbers(session, users, principal, listType, now);
		}
		return phoneNumbers;
	}

	private List<NumberBlock> loadNumbers(SqlSession session, Users users, String principal, ListType listType, long now) {
		SpamReports reports = session.getMapper(SpamReports.class);
		BlockList blocklist = session.getMapper(BlockList.class);
		
		long userId = users.getUserId(principal);
		
		List<String> personalizations = blocklist.getPersonalizations(userId);
		Set<String> exclusions = blocklist.getExcluded(userId);
		
		if (personalizations.isEmpty() && exclusions.isEmpty()) {
			return getCommonNumbers(reports, listType, now);
		}
		
		return loadNumbers(reports, personalizations, exclusions, listType, now);
	}

	public List<NumberBlock> lookupBlockList(ListType listType, long now) {
		try (SqlSession session = _db.db().openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			return getCommonNumbers(reports, listType, now);
		}
	}
	
	private List<NumberBlock> getCommonNumbers(SpamReports reports, ListType listType, long now) {
		List<NumberBlock> cachedNumbers = _numberCache.lookup(listType);
		if (cachedNumbers != null) {
			return cachedNumbers;
		}
		
		List<NumberBlock> numbers = loadNumbers(reports, Collections.emptyList(), Collections.emptySet(), listType, now);
		return _numberCache.put(listType, numbers);
	}

	private List<NumberBlock> loadNumbers(SpamReports reports, List<String> personalizations, Set<String> exclusions,
			ListType listType, long now) {
		boolean nationalOnly = listType.isNationalOnly();
		String dialPrefix = listType.getDialPrefix();

		List<DBNumberInfo> result = reports.getReports();
		NumberTree numberTree = new NumberTree();
		for (DBNumberInfo report : result) {
			String phoneId = report.getPhone();
			String phone = NumberAnalyzer.toInternationalFormat(phoneId);

			// Only filter by dial prefix if it's valid and national-only mode is enabled
			if (nationalOnly && !phone.startsWith(dialPrefix)) {
				continue;
			}
			
			int votes = report.getVotes();
			int ageInDays = (int) ((now - report.getUpdated()) / 1000 / 60 / 60 / 24);
			
			numberTree.insert(phone, votes, ageInDays);
		}

		// Enter white-listed numbers with with negative weight to prevent adding those numbers to wildcard blocks. 
		Set<String> whitelist = reports.getWhiteList();
		for (String phone : whitelist) {
			phone = NumberAnalyzer.toInternationalFormat(phone);
			
			numberTree.insert(phone, -1_000_000, 0);
		}
		for (String phoneId : exclusions) {
			String phone = NumberAnalyzer.toInternationalFormat(phoneId);

			numberTree.insert(phone, -1_000_000, 0);
		}
		
		// Make sure to override whitelist entries with personal blacklist entries.
		for (String phoneId : personalizations) {
			String phone = NumberAnalyzer.toInternationalFormat(phoneId);

			numberTree.insert(phone, 10_000_000, 0);
		}
		
		if (listType.useWildcards()) {
			numberTree.markWildcards();
		}
		
		return numberTree.createNumberBlocks(listType.getMinVotes(), listType.getMaxLength(), listType.getDialPrefix());
	}

	private static final class Cache<K, V> {
		
		private static final long FLUSH_INTERVAL_NANOS = 1_000_000_000L * 60 * 1;

		private final ConcurrentMap<K, CacheEntry<K, V>> _index = new ConcurrentHashMap<>();
		
		private transient long _lastFlush;

		private String _name;

		/** 
		 * Creates a {@link AddressBookCache.Cache}.
		 */
		public Cache(String name) {
			_name = name;
			_lastFlush = nanoTime();
		}
		
		public V put(K key, V value) {
			long now = nanoTime();
			flushCache(now);
			_index.put(key, CacheEntry.create(now, key, value));
			return value;
		}

		/**
		 * Removes an entry with the given key.
		 */
		public void flush(K key) {
			_index.remove(key);
		}
		
		public V lookup(K key) {
			CacheEntry<K, V> entry = _index.get(key);
			if (entry != null) {
				long now = nanoTime();
				if (!entry.isStale(now)) {
					entry.cacheHit(now);
					return entry.get();
				}
			}
			return null;
		}

		/** 
		 * The current time in nanoseconds since system boot.
		 */
		private long nanoTime() {
			return System.nanoTime();
		}

		private void flushCache(long now) {
			if (now - _lastFlush < FLUSH_INTERVAL_NANOS) {
				return;
			}
			_lastFlush = now;
			
			int cacheSize = _index.size();
			
			List<CacheEntry<K, ?>> expiredEntries = _index.values().stream().filter(e -> e.isExpired(now)).collect(Collectors.toList());
			int expiredSize = expiredEntries.size();
			
			LOG.info("Dropping {} expired out of {} entries from {} cache.", expiredSize, cacheSize, _name);
			
			for (CacheEntry<K, ?> expiredEntry : expiredEntries) {
				_index.remove(expiredEntry.getKey(), expiredEntry);
			}
		}

	}

	private static final class CacheEntry<K, V> {

		/**
		 * Time a cache entry survives, even it is not used.
		 */
		private static final long UNUSED_CACHE_NANOS = 1_000_000_000L * 60 * 5;
		
		/**
		 * Maximum time, a cache entry is kept, even if it is regularly used.
		 */
		private static final long MAX_CACHE_NANOS = 1_000_000_000L * 60 * 15;
		
		private final K _key;
		private final V _value;
		
		private final long _created;
		private transient long _lastAccess;

		/** 
		 * Creates a {@link CacheEntry}.
		 */
		public CacheEntry(long now, K key, V value) {
			_key = key;
			_value = value;
			_lastAccess = _created = now;
		}
		
		public K getKey() {
			return _key;
		}

		public static <K,V> CacheEntry<K,V> create(long now, K key, V value) {
			return new CacheEntry<>(now, key, value);
		}

		public void cacheHit(long now) {
			_lastAccess = now;
		}

		public V get() {
			return _value;
		}

		private boolean isExpired(long now) {
			return (now - _lastAccess) > UNUSED_CACHE_NANOS || isStale(now);
		}

		private boolean isStale(long now) {
			return (now - _created) > MAX_CACHE_NANOS;
		}
		
	}
}
