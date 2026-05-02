/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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

	private final Cache<ListType, CommonList> _numberCache = new Cache<>("common");

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
	 * Drops every entry from both the per-user and the per-{@link ListType} cache.
	 * Called after a new blocklist release is published so all clients see the
	 * fresh content immediately rather than waiting up to the cache TTL.
	 */
	public void flushAllCaches() {
		_userCache.flushAll();
		_numberCache.flushAll();
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
			if (LOG.isDebugEnabled()) {
				LOG.debug("Address book served from cache for: " + principal);
			}
			return cachedResult;
		}

		long now = System.currentTimeMillis();
		LoadResult result = computeBlocks(principal, now, settings);
		LOG.info("Address book computed for: " + principal
			+ " (" + result.path + ", " + result.blocks.size() + " blocks)");
		AddressBookResource addressBook = new AddressBookResource(rootUrl, serverRoot, resourcePath, principal,
				result.blocks, result.etag);
		return _userCache.put(principal, addressBook);
	}

	/**
	 * Compute the blocks for the given user without caching the result. Visible for
	 * testing; the production path goes through {@link #lookupAddressBook}.
	 */
	List<NumberBlock> loadNumbers(String principal, long now, UserSettings settings) {
		return computeBlocks(principal, now, settings).blocks;
	}

	private LoadResult computeBlocks(String principal, long now, UserSettings settings) {
		ListType listType = ListType.valueOf(settings.getDialPrefix(), settings.getMinVotes(),
				settings.getMaxLength(), settings.isWildcards(), settings.isNationalOnly());

		try (SqlSession session = _db.db().openSession()) {
			Users users = session.getMapper(Users.class);
			BlockList blocklist = session.getMapper(BlockList.class);
			SpamReports reports = session.getMapper(SpamReports.class);

			long userId = users.getUserId(principal);
			List<String> personalizations = blocklist.getPersonalizations(userId);
			Set<String> exclusions = blocklist.getExcluded(userId);

			if (personalizations.isEmpty() && exclusions.isEmpty()) {
				CommonList common = getCommonList(reports, listType, now);
				int settingsHash = listType.hashCode();
				String etag = CollectionEtag.compose(common.blocksHash(),
					CollectionEtag.hashPersonalSingletons(List.of()), settingsHash);
				return new LoadResult(common.blocks(), settingsHash, etag, "common-only");
			}

			CommonList common = getCommonList(reports, listType, now);
			int settingsHash = listType.hashCode() ^ personalSettingsHash(personalizations, exclusions);

			if (hasEffectiveExclusion(exclusions, common)) {
				// Rare path (~0.3% of users): a personal exclusion intersects the
				// common list and would change wildcard structure. Run the full
				// per-user pipeline.
				List<NumberBlock> blocks = loadNumbersFull(reports, personalizations, exclusions, listType, now);
				String etag = CollectionEtag.forFullPipeline(blocks, settingsHash);
				return new LoadResult(blocks, settingsHash, etag, "full pipeline");
			}

			// Common buckets + personal singletons (deduplicated against common).
			List<NumberBlock> blocks = new ArrayList<>(common.blocks());
			List<String> personalSingletons = new ArrayList<>();
			for (String phoneId : personalizations) {
				String phone = NumberAnalyzer.toInternationalFormat(phoneId);
				if (common.covers(phone)) {
					continue;
				}
				blocks.add(new NumberBlock(phone, List.of(phone)));
				personalSingletons.add(phone);
			}
			String etag = CollectionEtag.compose(common.blocksHash(),
				CollectionEtag.hashPersonalSingletons(personalSingletons), settingsHash);
			return new LoadResult(blocks, settingsHash, etag, "common+personal");
		}
	}

	private static final class LoadResult {
		final List<NumberBlock> blocks;
		final int settingsHash;
		final String etag;
		final String path;

		LoadResult(List<NumberBlock> blocks, int settingsHash, String etag, String path) {
			this.blocks = blocks;
			this.settingsHash = settingsHash;
			this.etag = etag;
			this.path = path;
		}
	}

	/**
	 * Looks up the unmodified common block list for the given list type. Public API,
	 * intended for callers that need the shared list without per-user modification.
	 */
	public List<NumberBlock> lookupBlockList(ListType listType, long now) {
		try (SqlSession session = _db.db().openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return getCommonList(reports, listType, now).blocks();
		}
	}

	private CommonList getCommonList(SpamReports reports, ListType listType, long now) {
		CommonList cached = _numberCache.lookup(listType);
		if (cached != null) {
			return cached;
		}
		List<NumberBlock> blocks = loadNumbersFull(reports, Collections.emptyList(), Collections.emptySet(),
				listType, now);
		return _numberCache.put(listType, new CommonList(blocks));
	}

	/**
	 * Returns true if any exclusion would actually subtract a number from what the
	 * user sees in the common list — either as a concrete entry or as a number
	 * covered by a wildcard. Exclusions on numbers that are not in the common list
	 * are no-ops and the user can still be served from the common cache.
	 */
	static boolean hasEffectiveExclusion(Set<String> exclusions, CommonList common) {
		for (String phoneId : exclusions) {
			String phone = NumberAnalyzer.toInternationalFormat(phoneId);
			if (common.covers(phone)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Hash mixing personalizations and exclusions into the collection ETag so that
	 * two users with the same common list but different personal lists see distinct
	 * ETags.
	 */
	private static int personalSettingsHash(List<String> personalizations, Set<String> exclusions) {
		List<String> sortedP = new ArrayList<>(personalizations);
		Collections.sort(sortedP);
		List<String> sortedE = new ArrayList<>(exclusions);
		Collections.sort(sortedE);
		int hash = 1;
		for (String s : sortedP) {
			hash = 31 * hash + s.hashCode();
		}
		hash = 31 * hash + 0x55555555;
		for (String s : sortedE) {
			hash = 31 * hash + s.hashCode();
		}
		return hash;
	}

	private List<NumberBlock> loadNumbersFull(SpamReports reports, List<String> personalizations, Set<String> exclusions,
			ListType listType, long now) {
		boolean nationalOnly = listType.isNationalOnly();
		String dialPrefix = listType.getDialPrefix();

		// Use the snapshot from the last released blocklist version so the address-book
		// content (and its ETag) only changes once per release, not on every individual
		// vote. PUBLISHED_VOTES is aliased into VOTES, PUBLISHED_LASTPING into LASTPING.
		List<DBNumberInfo> result = reports.getPublishedReports();
		NumberTree numberTree = new NumberTree();
		for (DBNumberInfo report : result) {
			String phoneId = report.getPhone();
			String phone = NumberAnalyzer.toInternationalFormat(phoneId);

			// Only filter by dial prefix if it's valid and national-only mode is enabled
			if (nationalOnly && !phone.startsWith(dialPrefix)) {
				continue;
			}

			int votes = report.getVotes();
			int ageInDays = (int) ((now - report.getLastPing()) / 1000 / 60 / 60 / 24);
			
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
		
		return numberTree.createNumberBlocksByPrefix(listType.getMinVotes(), listType.getMaxLength(), listType.getDialPrefix());
	}

	/**
	 * Common (per-{@link ListType}) block list with side-indexes used to test whether
	 * a given phone number would already be covered by the common list — either by
	 * a concrete bucket member or by a wildcard.
	 */
	static final class CommonList {
		private final List<NumberBlock> _blocks;
		private final Set<String> _concrete;
		private final List<String> _wildcardPrefixes;
		private final String _blocksHash;

		CommonList(List<NumberBlock> blocks) {
			_blocks = blocks;
			Set<String> concrete = new HashSet<>();
			List<String> wildcards = new ArrayList<>();
			for (NumberBlock b : blocks) {
				for (String n : b.getNumbers()) {
					if (n.endsWith("*")) {
						wildcards.add(n.substring(0, n.length() - 1));
					} else {
						concrete.add(n);
					}
				}
			}
			Collections.sort(wildcards);
			_concrete = concrete;
			_wildcardPrefixes = wildcards;
			_blocksHash = CollectionEtag.hashBlocks(blocks);
		}

		List<NumberBlock> blocks() {
			return _blocks;
		}

		/**
		 * Precomputed hash of the {@link #blocks()} content, fed into the
		 * collection ETag composition for every user that shares this common
		 * list. Computed once per refresh of this cache entry.
		 */
		String blocksHash() {
			return _blocksHash;
		}

		/**
		 * True if {@code number} (in international format) is already covered by the
		 * common list — concretely or under a wildcard.
		 */
		boolean covers(String number) {
			if (_concrete.contains(number)) {
				return true;
			}
			for (String prefix : _wildcardPrefixes) {
				if (number.startsWith(prefix)) {
					return true;
				}
			}
			return false;
		}
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

		/**
		 * Drops every entry from this cache.
		 */
		public void flushAll() {
			_index.clear();
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
