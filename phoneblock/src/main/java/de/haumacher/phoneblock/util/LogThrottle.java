/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Suppresses repeated log messages for the same condition, so that a single
 * misbehaving client cannot flood the log.
 *
 * <p>
 * The first occurrence of a condition (identified by a key) is reported. Further
 * occurrences of the same key within the time window are suppressed and counted.
 * The first occurrence after the window has elapsed is reported again, together
 * with the number of occurrences suppressed since the last report, and starts a
 * new window.
 * </p>
 *
 * <pre>
 * long suppressed = THROTTLE.acquire(key);
 * if (suppressed >= 0) {
 *     LOG.warn("Something failed ({} similar suppressed).", suppressed);
 * }
 * </pre>
 *
 * <p>
 * Keys must come from a small, bounded set (e.g. an exception class name), never
 * from request data: one state entry is kept per key for the lifetime of the
 * throttle.
 * </p>
 *
 * <p>
 * Thread-safe.
 * </p>
 */
public final class LogThrottle {

	/** Return value of {@link #acquire(String)} for an occurrence that must not be logged. */
	public static final long SUPPRESS = -1;

	private final long _windowMillis;

	private final LongSupplier _clock;

	private final ConcurrentHashMap<String, State> _states = new ConcurrentHashMap<>();

	private static final class State {
		long windowStart;
		long suppressed;

		State(long windowStart) {
			this.windowStart = windowStart;
		}
	}

	/**
	 * Creates a {@link LogThrottle} using the system clock.
	 *
	 * @param windowMillis The time in milliseconds during which repeats of a logged condition are suppressed.
	 */
	public LogThrottle(long windowMillis) {
		this(windowMillis, System::currentTimeMillis);
	}

	/**
	 * Creates a {@link LogThrottle} with an explicit clock (for testing).
	 *
	 * @param windowMillis The time in milliseconds during which repeats of a logged condition are suppressed.
	 * @param clock Source of the current time in milliseconds.
	 */
	public LogThrottle(long windowMillis, LongSupplier clock) {
		_windowMillis = windowMillis;
		_clock = clock;
	}

	/**
	 * Records an occurrence of the condition with the given key.
	 *
	 * @return {@link #SUPPRESS} (negative) if this occurrence must not be logged,
	 *         otherwise the number of occurrences of the same key that were suppressed since
	 *         the last reported one (<code>0</code> for the first occurrence).
	 */
	public long acquire(String key) {
		long now = _clock.getAsLong();
		long[] result = { SUPPRESS };
		_states.compute(key, (k, state) -> {
			if (state == null) {
				result[0] = 0;
				return new State(now);
			}
			if (now - state.windowStart >= _windowMillis) {
				result[0] = state.suppressed;
				state.windowStart = now;
				state.suppressed = 0;
			} else {
				state.suppressed++;
			}
			return state;
		});
		return result[0];
	}

}
