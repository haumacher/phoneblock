/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.Serializable;

/**
 * Fixed-window limit for wrong link codes entered on the <code>/link</code> page, kept in the
 * user's session.
 *
 * <p>
 * Guessing one of 20<sup>8</sup> codes is hopeless anyway; the limit keeps scripted attempts out
 * of the logs.
 * </p>
 */
public class WrongCodeLimit implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Number of wrong codes accepted within one {@link #WINDOW_MS window}. */
	public static final int MAX_FAILURES = 5;

	/** Length of the window in milliseconds. */
	public static final long WINDOW_MS = 60_000;

	private long _windowStart;

	private int _failures;

	/**
	 * Whether another code may be checked at the given time.
	 */
	public synchronized boolean allowed(long now) {
		if (now - _windowStart >= WINDOW_MS) {
			return true;
		}
		return _failures < MAX_FAILURES;
	}

	/**
	 * Records a wrong code at the given time.
	 */
	public synchronized void failed(long now) {
		if (now - _windowStart >= WINDOW_MS) {
			_windowStart = now;
			_failures = 0;
		}
		_failures++;
	}

}
