/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import de.haumacher.phoneblock.app.AuthContext;

/**
 * Short-lived cache of successful HTTP basic authentications.
 *
 * <p>
 * A Fritz!Box sync is a burst of about 27 authenticated CardDAV requests within
 * some seconds, each carrying the same {@code Authorization} header. Without this
 * cache, every one of them looks up the token, the user name and the user
 * settings in the database. An entry lives a fixed {@link #TTL_MS} after its
 * creation (it is not extended by use), so one sync authenticates against the
 * database about once, while changes reach the next request after the TTL at
 * the latest.
 * </p>
 *
 * <p>
 * Entries are keyed by a SHA-256 hash of the header, so no credentials are kept
 * in memory. Only successful authentications are cached; a wrong password is
 * checked against the database every time. Revoking a token, changing a password
 * or changing user settings drops the user's entries immediately (see the
 * {@code flush*} methods).
 * </p>
 */
final class AuthCache {

	/**
	 * Time an entry is valid after creation.
	 */
	static final long TTL_MS = 30_000;

	/**
	 * Upper bound of entries. Reaching it drops all entries; with the short TTL
	 * this only happens under an unusual load of distinct valid credentials.
	 */
	static final int MAX_ENTRIES = 50_000;

	private record Entry(AuthContext context, long expires) {
	}

	private final ConcurrentMap<String, Entry> _entries = new ConcurrentHashMap<>();

	private volatile long _nextCleanup;

	/**
	 * Incremented on every flush. An authentication that was checked against the
	 * database while a flush happened is not cached, since it may reflect the
	 * state before the change.
	 */
	private final AtomicLong _generation = new AtomicLong();

	/**
	 * The cache key for an {@code Authorization} header.
	 */
	static String key(String authHeader) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(authHeader.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

	/**
	 * The cached authentication for the given key, or {@code null}.
	 */
	AuthContext lookup(String key, long now) {
		Entry entry = _entries.get(key);
		if (entry == null) {
			return null;
		}
		if (now >= entry.expires()) {
			_entries.remove(key, entry);
			return null;
		}
		return entry.context();
	}

	/**
	 * The current generation, to be retrieved before checking credentials
	 * against the database and passed to {@link #put(String, AuthContext, long, long)}.
	 */
	long generation() {
		return _generation.get();
	}

	/**
	 * Caches a successful authentication.
	 *
	 * @param generation
	 *        The {@link #generation()} before the credentials were checked. If a
	 *        flush happened since then, the result is not cached.
	 */
	void put(String key, AuthContext context, long now, long generation) {
		if (_generation.get() != generation) {
			return;
		}
		if (now >= _nextCleanup) {
			_nextCleanup = now + TTL_MS;
			_entries.values().removeIf(e -> now >= e.expires());
		}
		if (_entries.size() >= MAX_ENTRIES) {
			_entries.clear();
		}
		Entry entry = new Entry(context, now + TTL_MS);
		_entries.put(key, entry);
		if (_generation.get() != generation) {
			// A flush raced with the insert.
			_entries.remove(key, entry);
		}
	}

	/**
	 * Drops all entries of the user with the given ID.
	 */
	void flushUser(long userId) {
		flushIf(c -> c.getAuthorization().getUserId() == userId);
	}

	/**
	 * Drops all entries of the user with the given login name.
	 */
	void flushUser(String login) {
		flushIf(c -> login.equals(c.getUserName()));
	}

	/**
	 * Drops all entries authenticated by the token with the given ID.
	 */
	void flushToken(long tokenId) {
		flushIf(c -> c.getAuthorization().getId() == tokenId);
	}

	private void flushIf(Predicate<AuthContext> test) {
		_generation.incrementAndGet();
		_entries.values().removeIf(e -> test.test(e.context()));
	}

	/**
	 * The number of entries, for testing.
	 */
	int size() {
		return _entries.size();
	}
}
