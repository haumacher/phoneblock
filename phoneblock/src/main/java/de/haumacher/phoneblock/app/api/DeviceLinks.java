/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory store of open device-code links (#621, RFC 8628 style).
 *
 * <p>
 * A device without a way to receive the redirect of the regular sign-in (the dongle's Linux
 * service behind Home Assistant ingress) {@link #start(String, String, String) starts} a link and
 * shows the user code. The user confirms the code on the <code>/link</code> page
 * ({@link #approve(String, String)} or {@link #deny(String, String)}), and the device collects the
 * result by {@link #poll(String, String, TokenFactory) polling} with the secret device code.
 * </p>
 *
 * <p>
 * The API token is only created when an approved link is collected, so an approval nobody picks up
 * leaves no credential behind. Each link is single-use: it is removed when its result is
 * delivered. Open links are kept in memory only; a server restart drops them and the user starts
 * again.
 * </p>
 *
 * <p>
 * All operations are synchronized on this instance. The store holds at most
 * {@link #MAX_TOTAL} small entries, so a single lock is cheap and keeps the indexes by device and
 * user code consistent.
 * </p>
 */
public class DeviceLinks {

	private static final Logger LOG = LoggerFactory.getLogger(DeviceLinks.class);

	/** Lifetime of a link in seconds. */
	public static final int EXPIRES_IN = 600;

	/** Minimum poll interval in seconds announced to the device. */
	public static final int INTERVAL = 5;

	/**
	 * Tolerance in milliseconds for polls that arrive slightly before the interval has passed
	 * (network and scheduling jitter on the device). Polls earlier than that answer
	 * {@link #SLOW_DOWN}.
	 */
	static final long POLL_GRACE_MS = 1000;

	/** Maximum number of open links started from one client address. */
	public static final int MAX_PER_CLIENT = 20;

	/** Maximum number of open links in total. */
	public static final int MAX_TOTAL = 10_000;

	/** Maximum length of a device label. */
	public static final int MAX_LABEL_LENGTH = 64;

	/** Maximum length of an app ID kept for logging. */
	static final int MAX_APP_ID_LENGTH = 64;

	/** Label used when the device does not send one. */
	public static final String DEFAULT_LABEL = "PhoneBlock-Dongle";

	/** Letters of a user code: the RFC 8628 consonant alphabet (no vowels, so no words). */
	public static final String USER_CODE_ALPHABET = "BCDFGHJKLMNPQRSTVWXZ";

	/** Number of letters in a user code. */
	public static final int USER_CODE_LENGTH = 8;

	/** Poll status: not yet confirmed. */
	public static final String PENDING = "pending";

	/** Poll status: polled faster than {@link #INTERVAL}. */
	public static final String SLOW_DOWN = "slow_down";

	/** Poll status: confirmed, the response carries the token. */
	public static final String APPROVED = "approved";

	/** Poll status: refused by the user. */
	public static final String DENIED = "denied";

	/** Poll status: unknown, expired or already collected. */
	public static final String EXPIRED = "expired";

	private static final long SWEEP_INTERVAL_MS = 10_000;

	private static final DeviceLinks INSTANCE = new DeviceLinks(System::currentTimeMillis, new SecureRandom());

	/**
	 * The state of a link.
	 */
	public enum State {
		/** Waiting for the user's decision. */
		PENDING,

		/** Confirmed by the user, waiting to be collected by the device. */
		APPROVED,

		/** Refused by the user, waiting to be reported to the device. */
		DENIED;
	}

	/**
	 * Outcome of {@link DeviceLinks#approve(String, String)} and
	 * {@link DeviceLinks#deny(String, String)}.
	 */
	public enum Decision {
		/** The decision was recorded. */
		OK,

		/** No open link with this code (never existed, expired or already collected). */
		UNKNOWN,

		/** The link was already confirmed. */
		ALREADY_APPROVED,

		/** The link was already refused. */
		ALREADY_DENIED;
	}

	/**
	 * Creates the API token when an approved link is collected.
	 */
	public interface TokenFactory {
		/**
		 * Creates an API token for the given user.
		 *
		 * @return The token value handed to the device.
		 */
		String createToken(String userName, long now, String userAgent, String label);
	}

	/**
	 * Signals that a limit for open links is reached.
	 */
	public static class LimitExceeded extends Exception {
		private static final long serialVersionUID = 1L;

		LimitExceeded(String message) {
			super(message);
		}
	}

	/**
	 * The data of a started link as reported to the device.
	 */
	public record Started(String deviceCode, String userCode, int interval, int expiresIn) {
	}

	/**
	 * The result of a poll.
	 *
	 * @param status One of {@link #PENDING}, {@link #SLOW_DOWN}, {@link #APPROVED}, {@link #DENIED},
	 *        {@link #EXPIRED}.
	 * @param token The new API token, only for {@link #APPROVED}.
	 */
	public record PollResult(String status, String token) {
	}

	/**
	 * Read-only view of a link for the confirmation page.
	 */
	public record LinkInfo(String userCode, String label, String appId, State state) {
	}

	private static final class Entry {
		final String deviceCode;
		final String userCode;
		final String appId;
		final String label;
		final String client;
		final long expires;

		State state = State.PENDING;
		String user;
		long lastPoll;

		Entry(String deviceCode, String userCode, String appId, String label, String client, long expires) {
			this.deviceCode = deviceCode;
			this.userCode = userCode;
			this.appId = appId;
			this.label = label;
			this.client = client;
			this.expires = expires;
		}

		boolean isExpired(long now) {
			return now >= expires;
		}

		LinkInfo info() {
			return new LinkInfo(userCode, label, appId, state);
		}
	}

	private final LongSupplier _clock;
	private final Random _rnd;

	/** Open links by device code. */
	private final Map<String, Entry> _byDevice = new HashMap<>();

	/** Open links by normalized user code (letters only, upper case). */
	private final Map<String, Entry> _byUser = new HashMap<>();

	private long _lastSweep;

	/**
	 * Creates a {@link DeviceLinks} store.
	 *
	 * @param clock Source of the current time in milliseconds.
	 * @param rnd Source of randomness for device and user codes, must be a {@link SecureRandom}
	 *        in production.
	 */
	public DeviceLinks(LongSupplier clock, Random rnd) {
		_clock = clock;
		_rnd = rnd;
	}

	/**
	 * The store used by the application.
	 */
	public static DeviceLinks getInstance() {
		return INSTANCE;
	}

	/**
	 * Starts a new link.
	 *
	 * @param appId Identifier of the requesting app, must not be empty.
	 * @param label User-visible device name, <code>null</code> for {@link #DEFAULT_LABEL}.
	 * @param client Address of the requesting client, used for the per-client limit.
	 * @throws LimitExceeded If too many links are open from this client or in total.
	 */
	public synchronized Started start(String appId, String label, String client) throws LimitExceeded {
		long now = _clock.getAsLong();
		sweep(now);

		if (_byDevice.size() >= MAX_TOTAL) {
			LOG.warn("Refusing device link from '{}': {} links open.", client, _byDevice.size());
			throw new LimitExceeded("Zu viele offene Verknüpfungen, bitte später erneut versuchen.");
		}

		int fromClient = 0;
		for (Entry entry : _byDevice.values()) {
			if (entry.client.equals(client)) {
				fromClient++;
			}
		}
		if (fromClient >= MAX_PER_CLIENT) {
			LOG.info("Refusing device link from '{}': {} links open from this address.", client, fromClient);
			throw new LimitExceeded("Zu viele offene Verknüpfungen von dieser Adresse, bitte später erneut versuchen.");
		}

		String deviceCode;
		do {
			deviceCode = createDeviceCode();
		} while (_byDevice.containsKey(deviceCode));

		String userKey;
		do {
			userKey = createUserKey();
		} while (_byUser.containsKey(userKey));

		String cleanAppId = limit(appId.trim(), MAX_APP_ID_LENGTH);
		String cleanLabel = cleanLabel(label);
		Entry entry = new Entry(deviceCode, formatUserCode(userKey), cleanAppId, cleanLabel, client,
			now + EXPIRES_IN * 1000L);
		_byDevice.put(deviceCode, entry);
		_byUser.put(userKey, entry);

		LOG.info("Started device link {} for app '{}' ('{}') from '{}'.", entry.userCode, cleanAppId, cleanLabel, client);

		return new Started(deviceCode, entry.userCode, INTERVAL, EXPIRES_IN);
	}

	/**
	 * Polls the state of a link.
	 *
	 * <p>
	 * When the link is approved, the token is created by the given factory, the link is removed,
	 * and the token is returned. A denied link is removed when the denial is reported.
	 * </p>
	 *
	 * @param deviceCode The device code from {@link Started#deviceCode()}.
	 * @param userAgent The User-Agent of the polling device, stored with the token.
	 * @param tokens Creates the token on pickup.
	 */
	public PollResult poll(String deviceCode, String userAgent, TokenFactory tokens) {
		long now = _clock.getAsLong();
		Entry approved;
		synchronized (this) {
			sweepIfDue(now);

			Entry entry = deviceCode == null ? null : _byDevice.get(deviceCode);
			if (entry == null) {
				return new PollResult(EXPIRED, null);
			}
			if (entry.isExpired(now)) {
				remove(entry);
				return new PollResult(EXPIRED, null);
			}

			long last = entry.lastPoll;
			entry.lastPoll = now;
			if (last != 0 && now - last < INTERVAL * 1000L - POLL_GRACE_MS) {
				return new PollResult(SLOW_DOWN, null);
			}

			switch (entry.state) {
				case PENDING:
					return new PollResult(PENDING, null);
				case DENIED:
					remove(entry);
					LOG.info("Device link {} denial delivered.", entry.userCode);
					return new PollResult(DENIED, null);
				case APPROVED:
				default:
					// Single use: removed before the token exists, a concurrent poll sees "expired".
					remove(entry);
					approved = entry;
					break;
			}
		}

		String token = tokens.createToken(approved.user, now, userAgent, approved.label);
		LOG.info("Device link {} collected, token created for user {} (app '{}').", approved.userCode,
			approved.user, approved.appId);
		return new PollResult(APPROVED, token);
	}

	/**
	 * Looks up an open link by its user code.
	 *
	 * @param userCode The code as entered by the user, see {@link #normalizeUserCode(String)}.
	 * @return The link, or <code>null</code> if there is no open link with this code.
	 */
	public synchronized LinkInfo find(String userCode) {
		Entry entry = lookup(userCode);
		return entry == null ? null : entry.info();
	}

	/**
	 * Confirms an open link for the given user.
	 *
	 * @param userCode The code as entered by the user.
	 * @param userName The user who confirms, the token is created for this user.
	 */
	public synchronized Decision approve(String userCode, String userName) {
		return decide(userCode, userName, State.APPROVED);
	}

	/**
	 * Refuses an open link.
	 *
	 * @param userCode The code as entered by the user.
	 * @param userName The user who refuses (for logging).
	 */
	public synchronized Decision deny(String userCode, String userName) {
		return decide(userCode, userName, State.DENIED);
	}

	private Decision decide(String userCode, String userName, State decision) {
		Entry entry = lookup(userCode);
		if (entry == null) {
			return Decision.UNKNOWN;
		}
		switch (entry.state) {
			case APPROVED:
				return Decision.ALREADY_APPROVED;
			case DENIED:
				return Decision.ALREADY_DENIED;
			case PENDING:
			default:
				entry.state = decision;
				entry.user = userName;
				LOG.info("Device link {} {} by user {} (app '{}', '{}').", entry.userCode,
					decision == State.APPROVED ? "approved" : "denied", userName, entry.appId, entry.label);
				return Decision.OK;
		}
	}

	/**
	 * Number of open links (for tests and monitoring).
	 */
	public synchronized int size() {
		return _byDevice.size();
	}

	private Entry lookup(String userCode) {
		long now = _clock.getAsLong();
		sweepIfDue(now);

		String key = normalizeUserCode(userCode);
		if (key == null) {
			return null;
		}
		Entry entry = _byUser.get(key);
		if (entry == null) {
			return null;
		}
		if (entry.isExpired(now)) {
			remove(entry);
			return null;
		}
		return entry;
	}

	private void remove(Entry entry) {
		_byDevice.remove(entry.deviceCode);
		_byUser.remove(normalizeUserCode(entry.userCode));
	}

	private void sweepIfDue(long now) {
		if (now - _lastSweep >= SWEEP_INTERVAL_MS) {
			sweep(now);
		}
	}

	private void sweep(long now) {
		_lastSweep = now;
		for (Iterator<Entry> it = _byDevice.values().iterator(); it.hasNext();) {
			Entry entry = it.next();
			if (entry.isExpired(now)) {
				it.remove();
				_byUser.remove(normalizeUserCode(entry.userCode));
			}
		}
	}

	private String createDeviceCode() {
		byte[] bytes = new byte[32];
		_rnd.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String createUserKey() {
		StringBuilder result = new StringBuilder(USER_CODE_LENGTH);
		for (int n = 0; n < USER_CODE_LENGTH; n++) {
			result.append(USER_CODE_ALPHABET.charAt(_rnd.nextInt(USER_CODE_ALPHABET.length())));
		}
		return result.toString();
	}

	/**
	 * Formats a normalized user code for display (<code>XXXX-XXXX</code>).
	 */
	static String formatUserCode(String key) {
		int half = key.length() / 2;
		return key.substring(0, half) + "-" + key.substring(half);
	}

	/**
	 * Normalizes a user code as entered by the user: case is ignored, dashes and white space are
	 * removed.
	 *
	 * @return The code in upper case without separators, or <code>null</code> if the input cannot
	 *         be a user code.
	 */
	public static String normalizeUserCode(String input) {
		if (input == null) {
			return null;
		}
		StringBuilder result = new StringBuilder(USER_CODE_LENGTH);
		for (int n = 0, cnt = input.length(); n < cnt; n++) {
			char ch = input.charAt(n);
			if (ch == '-' || Character.isWhitespace(ch)) {
				continue;
			}
			char upper = Character.toUpperCase(ch);
			if (USER_CODE_ALPHABET.indexOf(upper) < 0) {
				return null;
			}
			if (result.length() == USER_CODE_LENGTH) {
				return null;
			}
			result.append(upper);
		}
		return result.length() == USER_CODE_LENGTH ? result.toString() : null;
	}

	/**
	 * The label stored for a link: trimmed, control characters removed, at most
	 * {@link #MAX_LABEL_LENGTH} characters, {@link #DEFAULT_LABEL} if empty.
	 */
	static String cleanLabel(String label) {
		if (label == null) {
			return DEFAULT_LABEL;
		}
		String clean = label.replaceAll("\\p{Cntrl}", "").trim();
		if (clean.isEmpty()) {
			return DEFAULT_LABEL;
		}
		return limit(clean, MAX_LABEL_LENGTH).trim();
	}

	private static String limit(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		int end = maxLength;
		if (Character.isHighSurrogate(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(0, end);
	}

}
