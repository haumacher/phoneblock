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

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberBlock;
import de.haumacher.phoneblock.analysis.NumberTree;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReport;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.UserSettings;

/**
 * Cache for user address books that keep the data in memory between requests.
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class AddressBookCache implements ServletContextListener {
	
	private static final long FLUSH_INTERVAL_NANOS = 1_000_000_000 * 60 * 1;
	
	private final Cache<String, AddressBookResource> _userCache = new Cache<>();

	private final Cache<Integer, List<NumberBlock>> _numberCache = new Cache<>();

	private static AddressBookCache _instance;
	
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
	
	public AddressBookResource lookupAddressBook(String rootUrl, String serverRoot, String resourcePath,
			String principal) {
		AddressBookResource cachedResult = _userCache.lookup(principal);
		if (cachedResult != null) {
			return cachedResult;
		}
		
		AddressBookResource addressBook = new AddressBookResource(rootUrl, serverRoot, resourcePath, principal, phoneNumbers(principal));
		return _userCache.put(principal, addressBook);
	}

	private List<NumberBlock> phoneNumbers(String principal) {
		try (SqlSession session = DBService.getInstance().openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			BlockList blocklist = session.getMapper(BlockList.class);
			Users users = session.getMapper(Users.class);
			UserSettings settings = users.getSettings(principal);
			
			long userId = users.getUserId(principal);
			
			List<String> personalizations = blocklist.getPersonalizations(userId);
			Set<String> exclusions = blocklist.getExcluded(userId);
			int minVotes = settings.getMinVotes();
			
			if (!personalizations.isEmpty() || !exclusions.isEmpty()) {
				return getCommonNumbers(reports, minVotes);
			}
			
			return loadNumbers(reports, personalizations, exclusions, minVotes);
		}
	}

	public List<NumberBlock> lookupBlockList(int minVotes) {
		try (SqlSession session = DBService.getInstance().openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			return getCommonNumbers(reports, minVotes);
		}
	}

	private List<NumberBlock> getCommonNumbers(SpamReports reports, int minVotes) {
		Integer key = Integer.valueOf(minVotes);
		List<NumberBlock> cachedNumbers = _numberCache.lookup(key);
		if (cachedNumbers != null) {
			return cachedNumbers;
		}
		
		List<NumberBlock> numbers = loadNumbers(reports, Collections.emptyList(), Collections.emptySet(), minVotes);
		return _numberCache.put(key, numbers);
	}

	private List<NumberBlock> loadNumbers(SpamReports reports, List<String> personalizations, Set<String> exclusions,
			int minVotes) {
		List<SpamReport> result = reports.getReports(minVotes);
		
		NumberTree numberTree = new NumberTree();
		for (SpamReport report : result) {
			String phone = report.getPhone();
			if (exclusions.contains(phone)) {
				continue;
			}
			
			numberTree.insert(phone, report.getVotes());
		}
		for (String personalization : personalizations) {
			numberTree.insert(personalization, 1000000);
		}
		numberTree.markWildcards();
		
		return numberTree.createNumberBlocks();
	}
	
	private static final class Cache<K, V> {
		
		private final ConcurrentMap<K, CacheEntry<K, V>> _index = new ConcurrentHashMap<>();
		
		private transient long _lastFlush;

		/** 
		 * Creates a {@link AddressBookCache.Cache}.
		 */
		public Cache() {
			_lastFlush = System.nanoTime();
		}
		
		public V put(K key, V value) {
			flushCache();
			_index.put(key, CacheEntry.create(key, value));
			return value;
		}

		public V lookup(K key) {
			CacheEntry<K, V> entry = _index.get(key);
			if (entry != null && !entry.isExpired()) {
				entry.cacheHit();
				return entry.get();
			}
			return null;
		}

		private void flushCache() {
			long now = System.nanoTime();
			if (now - _lastFlush < FLUSH_INTERVAL_NANOS) {
				return;
			}
			_lastFlush = now;
			
			List<CacheEntry<K, ?>> expiredEntries = _index.values().stream().filter(CacheEntry::isExpired).collect(Collectors.toList());
			for (CacheEntry<K, ?> expiredEntry : expiredEntries) {
				_index.remove(expiredEntry.getKey(), expiredEntry);
			}
		}

	}

	private static final class CacheEntry<K, V> {

		private static final long MAX_CACHE_NANOS = 1000_000_000L * 60 * 5;
		
		private final K _key;
		private final V _value;
		
		private transient long _lastAccess;

		/** 
		 * Creates a {@link CacheEntry}.
		 */
		public CacheEntry(K key, V value) {
			_key = key;
			_value = value;
			_lastAccess = System.nanoTime();
		}
		
		public K getKey() {
			return _key;
		}

		public static <K,V> CacheEntry<K,V> create(K key, V value) {
			return new CacheEntry<>(key, value);
		}

		public void cacheHit() {
			_lastAccess = System.nanoTime();
		}

		public V get() {
			return _value;
		}

		public boolean isExpired() {
			long now = System.nanoTime();
			return (now - _lastAccess) > MAX_CACHE_NANOS;
		}
		
	}
}
