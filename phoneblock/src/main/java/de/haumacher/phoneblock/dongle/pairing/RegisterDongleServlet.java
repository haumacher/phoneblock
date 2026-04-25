/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.dongle.pairing;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Endpoint the dongle calls once at boot when its "pairing" partition
 * holds a valid secret. Records the secret → LAN IP mapping with a short
 * TTL so the install page on phoneblock.net can locate the dongle.
 */
@WebServlet(urlPatterns = "/api/dongle/register")
public class RegisterDongleServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(RegisterDongleServlet.class);

	/**
	 * Hard cap on the request body. Legitimate payload is ~70 bytes, so
	 * 1 KiB is generous; anything larger is treated as a probe and
	 * rejected without parsing.
	 */
	static final int MAX_BODY_BYTES = 1024;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Cheap pre-check on the declared length. Clients that send no
		// Content-Length still go through the streaming cap below.
		int declared = req.getContentLength();
		if (declared > MAX_BODY_BYTES) {
			resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
			return;
		}

		byte[] body = req.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
		if (body.length > MAX_BODY_BYTES) {
			resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
			return;
		}

		RegisterRequest payload;
		try {
			payload = RegisterRequest.readRegisterRequest(
				new JsonReader(new ReaderAdapter(new StringReader(
					new String(body, StandardCharsets.UTF_8)))));
		} catch (RuntimeException | IOException ex) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		PairingRegistry registry = PairingRegistry.getInstance();
		if (registry == null) {
			resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			return;
		}

		PairingRegistry.RegisterResult result = registry.register(
			payload.getSecret(),
			payload.getLanIp(),
			clientPublicIp(req));

		switch (result) {
			case OK:
				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
				return;
			case BAD_REQUEST:
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return;
			case RATE_LIMITED:
				// HttpServletResponse predates RFC 6585; no constant for
				// 429. Plain status code is fine.
				resp.setStatus(429);
				resp.setHeader("Retry-After", "30");
				return;
			case MAP_FULL:
				LOG.warn("Pairing registry full ({} entries) — rejecting register from {}",
					registry.size(), clientPublicIp(req));
				resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				return;
		}
	}

	/**
	 * Best effort: behind Tomcat-via-reverse-proxy, X-Forwarded-For carries
	 * the original client; otherwise the socket address is the client.
	 */
	static String clientPublicIp(HttpServletRequest req) {
		String xff = req.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isEmpty()) {
			int comma = xff.indexOf(',');
			return (comma >= 0 ? xff.substring(0, comma) : xff).trim();
		}
		return req.getRemoteAddr();
	}
}
