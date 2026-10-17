package de.haumacher.phoneblock.db;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import de.haumacher.msgbuf.binary.OctetDataReader;
import de.haumacher.msgbuf.binary.OctetDataWriter;
import de.haumacher.phoneblock.util.LogThrottle;

public class TokenInfo {

	/**
	 * Throttles the warning for tokens that cannot be parsed: a broken client
	 * retries with the same token on every request.
	 */
	private static final LogThrottle PARSE_FAILURE_LOG = new LogThrottle(TimeUnit.MINUTES.toMillis(10));

	/**
	 * Throttles the message for well-formed tokens that are rejected (outdated or
	 * wrong secret).
	 */
	private static final LogThrottle REJECT_LOG = new LogThrottle(TimeUnit.MINUTES.toMillis(10));

	public long id;
	public byte[] secret;

	private TokenInfo(long id, byte[] secrets) {
		this.id = id;
		this.secret = secrets;
	}

	public static TokenInfo parse(String token) throws IOException {
		long id;
		byte[] secret;
		{
			byte[] tokenBytes = Base64.getDecoder().decode(token.substring(DB.TOKEN_VERSION.length()));
			ByteArrayInputStream in = new ByteArrayInputStream(tokenBytes);
			OctetDataReader reader = new OctetDataReader(in);
			reader.beginObject();
			reader.nextName();
			id = reader.nextLong();
			reader.nextName();
			secret = reader.nextBinary();
			reader.endObject();
		}
	
		return new TokenInfo(id, secret);
	}

	public static String createToken(long id, byte[] secret) throws IOError {
		try {
			ByteArrayOutputStream tokenStream = new ByteArrayOutputStream();
			OctetDataWriter writer = new OctetDataWriter(tokenStream);
			writer.beginObject();
			writer.name(1);
			writer.value(id);
			writer.name(2);
			writer.value(secret);
			writer.endObject();
			return DB.TOKEN_VERSION + Base64.getEncoder().withoutPadding().encodeToString(tokenStream.toByteArray());
		} catch (IOException ex) {
			throw new IOError(ex);
		}
	}

	/**
	 * Logs that the given token could not be processed, without revealing the
	 * token. Repeats of the same failure are throttled.
	 */
	public static void logParseFailure(Logger log, String token, Exception ex) {
		long suppressed = PARSE_FAILURE_LOG.acquire(ex.getClass().getName());
		if (suppressed != LogThrottle.SUPPRESS) {
			log.warn(describeParseFailure(token, ex, suppressed));
		}
	}

	/**
	 * Logs (at info level) that the given well-formed token was rejected, without
	 * revealing the token. Repeats with the same key are throttled, since a client
	 * presents the same token on every request.
	 *
	 * @param key Identifies the kind of rejection for throttling; must come from a fixed set.
	 * @param message Describes the rejection; must not contain the token.
	 */
	public static void logRejected(Logger log, String key, String message, String token) {
		long suppressed = REJECT_LOG.acquire(key);
		if (suppressed != LogThrottle.SUPPRESS) {
			log.info(describeRejected(message, token, suppressed));
		}
	}

	/**
	 * The log message for a rejected token, see {@link #logRejected(Logger, String, String, String)}.
	 */
	static String describeRejected(String message, String token, long suppressed) {
		return message + " (length " + token.length() + ", fingerprint '" + fingerprint(token) + "', "
				+ suppressed + " similar rejections suppressed since last report).";
	}

	/**
	 * The log message for a token that could not be parsed.
	 *
	 * <p>
	 * Contains only the token length, a short fingerprint for correlation and the
	 * exception class. The exception message is left out, since it may quote
	 * parts of the token (e.g. "Illegal base64 character").
	 * </p>
	 */
	static String describeParseFailure(String token, Exception ex, long suppressed) {
		return "Failed to parse authorization token (length " + token.length() + ", fingerprint '"
				+ fingerprint(token) + "'): " + ex.getClass().getName() + " (" + suppressed
				+ " similar failures suppressed since last report).";
	}

	/**
	 * A short, non-reversible fingerprint of a token, for correlating log
	 * messages without logging the token itself.
	 */
	public static String fingerprint(String token) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 4);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
