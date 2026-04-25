/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.dongle.pairing;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Short-lived in-memory registry mapping per-install pairing secrets to
 * the dongle's LAN IP, so the browser-side install page can navigate
 * straight to the freshly-flashed dongle.
 *
 * <p>The endpoint is unauthenticated by design — the secret itself is the
 * trust anchor. To stay robust against an attacker hammering the register
 * endpoint, three independent caps work in concert:
 *
 * <ul>
 *   <li>per-public-IP rate limit (token-bucket style; small leak rate),
 *   <li>hard cap on the number of live entries,
 *   <li>upstream body-size cap on the servlet (kept there, not here).
 * </ul>
 *
 * <p>Reaching the entry cap returns {@link RegisterResult#MAP_FULL} rather
 * than evicting the oldest live entry — eviction would let an attacker
 * deliberately flush legitimate first-time installs out of the registry.
 * Stale entries are pruned by a periodic sweep plus a lazy check on each
 * lookup; under sustained spam, real installs simply fall back to mDNS,
 * the same recovery path that already exists for OTA-only dongles.
 */
public class PairingRegistry implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(PairingRegistry.class);

	/** Lifetime of a registered secret. */
	public static final long TTL_MS = 30L * 60 * 1000;

	/** Hard cap on concurrent registrations. */
	public static final int MAX_ENTRIES = 10_000;

	/**
	 * Per-public-IP allowance for {@code /api/dongle/register}. Implemented
	 * as a token bucket: each call consumes 1 token, the bucket refills at
	 * {@link #RATE_REFILL_PERIOD_MS}. Empty bucket → request denied.
	 */
	public static final int RATE_BUCKET_CAPACITY = 6;

	/** Time between two refill ticks (one token added per period). */
	public static final long RATE_REFILL_PERIOD_MS = 10_000L;

	/**
	 * Hard cap on rate-bucket entries — same reasoning as {@link #MAX_ENTRIES}:
	 * a distributed attacker spreading across many IPs can fill this map,
	 * so the periodic sweep prunes idle buckets too.
	 */
	public static final int MAX_RATE_BUCKETS = 50_000;

	/** Sweep frequency for both maps. */
	public static final long SWEEP_PERIOD_S = 60;

	/** Validates the wire form of a secret. */
	private static final Pattern HEX32 = Pattern.compile("[0-9a-f]{32}");

	/** Validates the wire form of an IPv4 dotted-quad — strict, no spaces / no IPv6 / no hostnames. */
	private static final Pattern IPV4 = Pattern.compile(
		"(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
			+ "(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");

	/** Outcome of {@link #register(String, String, String)}. */
	public enum RegisterResult {
		OK,
		BAD_REQUEST,    // malformed secret/lanIp — caller should map to 400
		RATE_LIMITED,   // per-IP rate limit hit — caller should map to 429
		MAP_FULL        // global cap reached — caller should map to 503
	}

	private static volatile PairingRegistry _instance;

	private final ConcurrentHashMap<String, Entry>      _entries = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, RateBucket> _buckets = new ConcurrentHashMap<>();

	private final SchedulerService _scheduler;
	private final LongSupplier _clock;
	private ScheduledFuture<?> _sweepTask;

	public PairingRegistry(SchedulerService scheduler) {
		this(scheduler, System::currentTimeMillis);
	}

	/** Visible for testing — lets the test drive the clock without sleeping. */
	PairingRegistry(SchedulerService scheduler, LongSupplier clock) {
		_scheduler = scheduler;
		_clock = clock;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		if (_scheduler != null) {
			_sweepTask = _scheduler.scheduler().scheduleAtFixedRate(
				this::sweep, SWEEP_PERIOD_S, SWEEP_PERIOD_S, TimeUnit.SECONDS);
		}
		_instance = this;
		LOG.info("Pairing registry started (TTL {} min, cap {} entries).",
			TTL_MS / 60_000, MAX_ENTRIES);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_sweepTask != null) {
			_sweepTask.cancel(false);
			_sweepTask = null;
		}
		_entries.clear();
		_buckets.clear();
		if (_instance == this) {
			_instance = null;
		}
	}

	/**
	 * Returns the running registry, or {@code null} if the application is
	 * still starting up or already shutting down. Servlets check for null
	 * and respond with 503 in that window.
	 */
	public static PairingRegistry getInstance() {
		return _instance;
	}

	/**
	 * Records {@code secret → lanIp} keyed by the dongle's secret, charging
	 * one token to the {@code publicIp}'s rate bucket.
	 *
	 * <p>{@code publicIp} is purely an anti-abuse key (rate-limit), not a
	 * trust anchor: the secret is what the lookup matches against, and
	 * lookups don't compare public IPs.
	 */
	public RegisterResult register(String secret, String lanIp, String publicIp) {
		if (secret == null || !HEX32.matcher(secret).matches()) return RegisterResult.BAD_REQUEST;
		if (lanIp  == null || !IPV4.matcher(lanIp).matches())   return RegisterResult.BAD_REQUEST;

		if (publicIp != null && !consumeToken(publicIp)) {
			return RegisterResult.RATE_LIMITED;
		}

		// Race window: between size() and put() multiple threads can each
		// pass the check, so the actual map size can briefly exceed the
		// cap by however many threads are racing. That slack is bounded
		// by the servlet thread pool — fine for a 1.5 MB worst-case map.
		if (_entries.size() >= MAX_ENTRIES && !_entries.containsKey(secret)) {
			return RegisterResult.MAP_FULL;
		}

		_entries.put(secret, new Entry(lanIp, _clock.getAsLong()));
		return RegisterResult.OK;
	}

	/**
	 * Returns the LAN IP registered under {@code secret}, or {@code null}
	 * if no entry exists or the entry has expired. Expired entries found
	 * here are removed in passing (lazy cleanup).
	 */
	public String lookup(String secret) {
		if (secret == null || !HEX32.matcher(secret).matches()) return null;

		Entry e = _entries.get(secret);
		if (e == null) return null;

		long now = _clock.getAsLong();
		if (now - e.timestamp > TTL_MS) {
			// Drop only if the value is unchanged — a concurrent re-register
			// from the same dongle must not be wiped by a stale lookup.
			_entries.remove(secret, e);
			return null;
		}
		return e.lanIp;
	}

	/** Visible for tests / monitoring. */
	public int size() {
		return _entries.size();
	}

	/**
	 * Drops expired entries from both maps. Runs on a fixed interval AND
	 * is safe to call manually (tests).
	 */
	void sweep() {
		long now = _clock.getAsLong();
		int droppedEntries = pruneExpiredEntries(now);
		int droppedBuckets = pruneIdleBuckets(now);
		if (droppedEntries > 0 || droppedBuckets > 0) {
			LOG.debug("Pairing sweep: dropped {} entries, {} rate buckets; "
				+ "remaining: {} entries, {} buckets",
				droppedEntries, droppedBuckets, _entries.size(), _buckets.size());
		}
	}

	private int pruneExpiredEntries(long now) {
		int dropped = 0;
		for (Iterator<Map.Entry<String, Entry>> it = _entries.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Entry> me = it.next();
			if (now - me.getValue().timestamp > TTL_MS) {
				it.remove();
				dropped++;
			}
		}
		return dropped;
	}

	private int pruneIdleBuckets(long now) {
		// A bucket that has been at full capacity for at least one TTL
		// is indistinguishable from an idle one — drop it. This keeps
		// the rate-bucket map bounded under a long-running spammer that
		// rotates IPs.
		int dropped = 0;
		for (Iterator<Map.Entry<String, RateBucket>> it = _buckets.entrySet().iterator(); it.hasNext(); ) {
			RateBucket b = it.next().getValue();
			if (b.idleSince(now, TTL_MS)) {
				it.remove();
				dropped++;
			}
		}
		return dropped;
	}

	/** True if the per-IP bucket had a token to spend. */
	private boolean consumeToken(String publicIp) {
		// Drop new buckets when over the cap so a wide IP-rotation attack
		// can't grow the map without bound. Existing buckets continue to
		// serve traffic.
		RateBucket b = _buckets.get(publicIp);
		if (b == null) {
			if (_buckets.size() >= MAX_RATE_BUCKETS) {
				return false;
			}
			b = _buckets.computeIfAbsent(publicIp,
				k -> new RateBucket(RATE_BUCKET_CAPACITY, _clock.getAsLong()));
		}
		return b.tryConsume(_clock.getAsLong());
	}

	private static final class Entry {
		final String lanIp;
		final long   timestamp;

		Entry(String lanIp, long timestamp) {
			this.lanIp = lanIp;
			this.timestamp = timestamp;
		}
	}

	/**
	 * Token-bucket rate limiter. {@code capacity} tokens initially, one
	 * token added every {@link PairingRegistry#RATE_REFILL_PERIOD_MS}.
	 */
	private static final class RateBucket {
		private final int capacity;
		private double tokens;
		private long lastRefillMs;

		RateBucket(int capacity, long nowMs) {
			this.capacity = capacity;
			this.tokens = capacity;
			this.lastRefillMs = nowMs;
		}

		synchronized boolean tryConsume(long nowMs) {
			refill(nowMs);
			if (tokens >= 1.0) {
				tokens -= 1.0;
				return true;
			}
			return false;
		}

		private void refill(long nowMs) {
			long elapsed = nowMs - lastRefillMs;
			if (elapsed <= 0) return;
			double add = (double) elapsed / RATE_REFILL_PERIOD_MS;
			tokens = Math.min(capacity, tokens + add);
			lastRefillMs = nowMs;
		}

		synchronized boolean idleSince(long nowMs, long maxIdleMs) {
			refill(nowMs);
			return tokens >= capacity && (nowMs - lastRefillMs) >= maxIdleMs;
		}
	}
}
