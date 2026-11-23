/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.exchange;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.ExternalReports;
import de.haumacher.phoneblock.db.ExternalReports.ExportRow;

/**
 * Builds the partner report exchange feed: the spam reports made by PhoneBlock users themselves.
 *
 * <p>
 * An exported report is a personal block-list entry ({@code PERSONALIZATION} with
 * {@code BLOCKED = true}, no wildcard), windowed on its creation time. Imports (meta search, FTC,
 * partner feeds) never write {@code PERSONALIZATION}, so they are excluded by construction and
 * can never be echoed back to the partner.
 * Legitimate (allow-list) entries are never exported, and only reports on numbers with at least
 * {@link #MIN_VOTES} community votes are (corroboration filter).
 * </p>
 *
 * @see PartnerReportsServlet
 */
public class PartnerExport {

	/** Milliseconds of a day. */
	public static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

	/** Number of days before today that are available in the feed. */
	public static final int MAX_DAYS = 10;

	/**
	 * Minimum number of community votes ({@code NUMBERS.VOTES}, all sources including imports) a
	 * number must have when the feed is requested, for a user's report on it to be exported.
	 *
	 * <p>
	 * Keeps uncorroborated single reports (e.g. a user blocking the own number to try the filter)
	 * out of the feed, since an exported report cannot be withdrawn. Evaluated when the feed is
	 * requested; a report whose number has fewer votes at that time is simply not delivered.
	 * </p>
	 */
	public static final int MIN_VOTES = 2;

	/** Length (in hex characters) of the exported <code>id</code> and <code>senderId</code>. */
	static final int ID_LENGTH = 32;

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	/**
	 * A time window <code>[from, to)</code> in milliseconds since epoch.
	 */
	public record Window(long from, long to) {}

	/**
	 * The window to export, clamped to <code>[today 00:00 UTC - {@value #MAX_DAYS} days, today 00:00 UTC]</code>.
	 *
	 * @param from The requested start, <code>null</code> for the start of the previous UTC day.
	 * @param to The requested end (exclusive), <code>null</code> for the start of today (UTC).
	 * @param now The current time.
	 */
	public static Window window(Long from, Long to, long now) {
		long today = Math.floorDiv(now, DAY_MILLIS) * DAY_MILLIS;
		long min = today - MAX_DAYS * DAY_MILLIS;
		long max = today;

		long start = from == null ? today - DAY_MILLIS : clamp(from.longValue(), min, max);
		long end = to == null ? today : clamp(to.longValue(), min, max);
		return new Window(start, end);
	}

	private static long clamp(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * The reports of PhoneBlock users in the given window, sorted by creation time.
	 *
	 * @param mapper The DB access.
	 * @param secret The HMAC secret for the anonymized <code>id</code> and <code>senderId</code>.
	 * @param window The window to export, see {@link #window(Long, Long, long)}.
	 */
	public static List<PartnerReport> export(ExternalReports mapper, byte[] secret, Window window) {
		List<PartnerReport> result = new ArrayList<>();
		if (window.from() >= window.to()) {
			return result;
		}

		Mac mac = createMac(secret);
		for (ExportRow row : mapper.getBlockedEntries(window.from(), window.to(), MIN_VOTES)) {
			Rating rating = row.rating();
			if (rating == Rating.A_LEGITIMATE) {
				// Inconsistent rating for a block-list entry: a legitimate report is never exported.
				continue;
			}

			String phone;
			try {
				phone = NumberAnalyzer.toInternationalFormat(row.phone());
			} catch (RuntimeException ex) {
				continue;
			}

			String comment = row.comment();
			String text = comment == null || comment.isBlank() ? null : comment.strip();

			String senderId = hmac(mac, Long.toString(row.userId()));
			String id = hmac(mac, row.userId() + "|" + phone + "|" + row.created());

			result.add(new PartnerReport(id, phone, senderId, Long.valueOf(row.created()), text, Integer.valueOf(1),
				Integer.valueOf(typeId(rating))));
		}
		return result;
	}

	/**
	 * The partner's report category for the given PhoneBlock rating.
	 *
	 * @param rating The rating, <code>null</code> for a block-list entry without rating.
	 */
	static int typeId(Rating rating) {
		if (rating == null) {
			return 1;
		}
		return switch (rating) {
			case C_PING -> 10;
			case D_POLL -> 6;
			case E_ADVERTISING -> 5;
			case F_GAMBLE -> 3;
			case G_FRAUD -> 8;
			case A_LEGITIMATE, B_MISSED -> 1;
		};
	}

	private static Mac createMac(byte[] secret) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return mac;
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HMAC not available.", ex);
		}
	}

	/**
	 * Hex encoded HMAC-SHA256 of the given message, truncated to {@value #ID_LENGTH} characters.
	 */
	private static String hmac(Mac mac, String message) {
		byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(digest).substring(0, ID_LENGTH);
	}

	/**
	 * The secret as byte array, or <code>null</code> if not configured.
	 */
	static byte[] secretBytes(String secret) {
		if (secret == null || secret.isBlank()) {
			return null;
		}
		return secret.strip().getBytes(StandardCharsets.UTF_8);
	}

}
