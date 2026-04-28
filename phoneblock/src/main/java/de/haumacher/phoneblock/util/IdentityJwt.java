package de.haumacher.phoneblock.util;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.msgbuf.server.io.WriterAdapter;

/**
 * Signs and verifies short-lived identity assertions.
 *
 * <p>
 * Two flows currently use this primitive:
 * <ul>
 *   <li><b>Auth gate</b> ({@link #PURPOSE_AUTH_GATE}) — server proves to an
 *       on-prem device (e.g. the dongle) that a user successfully logged in.
 *       Carries a CSRF {@code nonce}; verified via {@code /auth/verify-code}.
 *   <li><b>Login ticket</b> ({@link #PURPOSE_LOGIN}) — device proves to the
 *       server that its API token's owner authorized opening a specific page
 *       in the user's browser. Carries the target path in {@code next};
 *       consumed once via {@code /auth/login-ticket}.
 * </ul>
 *
 * <p>
 * Format is a minimal HS256 JWT. The signing secret is generated at JVM
 * startup and lives in memory only — these tokens are short-lived, so a
 * server restart simply forces in-flight flows to be retried.
 *
 * <p>
 * The {@code purpose} claim is mandatory and is checked by
 * {@link #verify(String, String)} against the caller's expectation. This
 * prevents a JWT minted for one flow from being replayed against the
 * verifier of another.
 */
public final class IdentityJwt {

	/** Purpose claim for the dongle "Login with PhoneBlock" gate. */
	public static final String PURPOSE_AUTH_GATE = "auth-gate";

	/** Purpose claim for one-shot browser auto-login tickets. */
	public static final String PURPOSE_LOGIN = "login";

	/** TTL for {@link #signAuthGate(String, String)}. */
	public static final long AUTH_GATE_TTL_MS = 5 * 60 * 1000L;

	/** TTL for {@link #signLoginTicket(String, String)}. */
	public static final long LOGIN_TICKET_TTL_MS = 30 * 1000L;

	private static final byte[] SECRET = generateSecret();

	private static final byte[] HEADER_BYTES =
			"{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8);

	private static final String HEADER_B64 = b64url(HEADER_BYTES);

	private IdentityJwt() {
		// no instances
	}

	private static byte[] generateSecret() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return bytes;
	}

	/**
	 * Issues an auth-gate assertion that {@code userName} authenticated via the
	 * server-side login flow. Bound to {@code nonce} so a stolen JWT cannot be
	 * replayed against a different login attempt.
	 */
	public static String signAuthGate(String userName, String nonce) {
		return sign(userName, PURPOSE_AUTH_GATE, AUTH_GATE_TTL_MS,
				nonce == null ? "" : nonce, null);
	}

	/**
	 * Issues a one-shot login ticket that grants a browser session for
	 * {@code userName} and redirects to {@code next}. The path is part of the
	 * signed payload, so the consumer cannot be tricked into redirecting to a
	 * different target than the API caller authorized.
	 */
	public static String signLoginTicket(String userName, String next) {
		return sign(userName, PURPOSE_LOGIN, LOGIN_TICKET_TTL_MS,
				"", next == null ? "" : next);
	}

	private static String sign(String userName, String purpose, long ttlMs,
			String nonce, String next) {
		long expSec = (System.currentTimeMillis() + ttlMs) / 1000L;

		StringWriter buf = new StringWriter();
		try (JsonWriter w = new JsonWriter(new WriterAdapter(buf))) {
			w.beginObject();
			w.name("sub");     w.value(userName);
			w.name("exp");     w.value(expSec);
			w.name("purpose"); w.value(purpose);
			if (nonce != null && !nonce.isEmpty()) {
				w.name("nonce"); w.value(nonce);
			}
			if (next != null && !next.isEmpty()) {
				w.name("next"); w.value(next);
			}
			w.endObject();
		} catch (IOException e) {
			// StringWriter does not throw — propagate as unchecked.
			throw new IllegalStateException(e);
		}
		String payloadB64 = b64url(buf.toString().getBytes(StandardCharsets.UTF_8));

		String signingInput = HEADER_B64 + "." + payloadB64;
		String sigB64 = b64url(hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8)));

		return signingInput + "." + sigB64;
	}

	/**
	 * Holder for the verified claims of a token. Returned by
	 * {@link #verify(String, String)} on success.
	 */
	public static final class Claims {
		public final String sub;
		public final String purpose;
		public final String nonce;
		public final String next;
		public final long expSec;

		Claims(String sub, String purpose, String nonce, String next, long expSec) {
			this.sub = sub;
			this.purpose = purpose;
			this.nonce = nonce;
			this.next = next;
			this.expSec = expSec;
		}
	}

	/** Thrown when a token is malformed, signed with a different key, or expired. */
	public static final class InvalidTokenException extends Exception {
		private static final long serialVersionUID = 1L;
		InvalidTokenException(String message) { super(message); }
	}

	/**
	 * Verifies signature, expiry and {@code purpose}, returns the decoded claims.
	 * Throws {@link InvalidTokenException} on any failure — the caller
	 * should treat the token as if it had never been seen.
	 *
	 * @param expectedPurpose the {@code purpose} claim the token must carry;
	 *        rejects everything else, including legacy tokens that predate the
	 *        purpose claim (those expire within their original TTL).
	 */
	public static Claims verify(String jwt, String expectedPurpose) throws InvalidTokenException {
		if (jwt == null) throw new InvalidTokenException("missing token");
		int dot1 = jwt.indexOf('.');
		int dot2 = dot1 < 0 ? -1 : jwt.indexOf('.', dot1 + 1);
		if (dot1 < 0 || dot2 < 0 || jwt.indexOf('.', dot2 + 1) >= 0) {
			throw new InvalidTokenException("malformed token");
		}
		String headerB64 = jwt.substring(0, dot1);
		String payloadB64 = jwt.substring(dot1 + 1, dot2);
		String sigB64 = jwt.substring(dot2 + 1);

		// Reject anything that does not match our exact header (alg/typ).
		// This pins the algorithm to HS256 and rejects "alg":"none" attacks.
		if (!HEADER_B64.equals(headerB64)) {
			throw new InvalidTokenException("unexpected header");
		}

		byte[] expectedSig = hmacSha256((headerB64 + "." + payloadB64)
				.getBytes(StandardCharsets.UTF_8));
		byte[] actualSig;
		try {
			actualSig = Base64.getUrlDecoder().decode(sigB64);
		} catch (IllegalArgumentException e) {
			throw new InvalidTokenException("bad signature encoding");
		}
		if (!constantTimeEquals(expectedSig, actualSig)) {
			throw new InvalidTokenException("signature mismatch");
		}

		byte[] payload;
		try {
			payload = Base64.getUrlDecoder().decode(payloadB64);
		} catch (IllegalArgumentException e) {
			throw new InvalidTokenException("bad payload encoding");
		}

		String sub = null, nonce = null, next = null, purpose = null;
		long expSec = 0;
		try (JsonReader json = new JsonReader(new ReaderAdapter(
				new StringReader(new String(payload, StandardCharsets.UTF_8))))) {
			json.beginObject();
			while (json.hasNext()) {
				switch (json.nextName()) {
					case "sub":     sub = json.nextString(); break;
					case "nonce":   nonce = json.nextString(); break;
					case "next":    next = json.nextString(); break;
					case "purpose": purpose = json.nextString(); break;
					case "exp":     expSec = json.nextLong(); break;
					default:        json.skipValue();
				}
			}
			json.endObject();
		} catch (IOException e) {
			throw new InvalidTokenException("malformed payload");
		}
		if (sub == null) throw new InvalidTokenException("missing sub");
		if (System.currentTimeMillis() / 1000L > expSec) {
			throw new InvalidTokenException("expired");
		}
		if (!expectedPurpose.equals(purpose)) {
			throw new InvalidTokenException("purpose mismatch");
		}
		return new Claims(sub, purpose,
				nonce == null ? "" : nonce,
				next == null ? "" : next,
				expSec);
	}

	private static byte[] hmacSha256(byte[] data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
			return mac.doFinal(data);
		} catch (Exception e) {
			throw new IllegalStateException("HMAC-SHA256 unavailable", e);
		}
	}

	private static String b64url(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}

	private static boolean constantTimeEquals(byte[] a, byte[] b) {
		if (a.length != b.length) return false;
		int diff = 0;
		for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
		return diff == 0;
	}
}
