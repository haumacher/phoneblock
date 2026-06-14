/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.dongle.logreport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.LoginFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Receives the captured WARN/ERROR log ring that a PhoneBlock dongle ships
 * (best-effort, piggybacked on its daily self-test) when it has new error
 * lines to report. Unlike {@code CoredumpServlet} this is for the
 * non-crashing failure modes — a dongle that keeps running but, say, lost
 * its SIP registration — which would otherwise only ever be visible on the
 * device's local web UI.
 *
 * <p>The body is plain text, one error per line
 * ({@code <E|W> +<uptime>s <tag>: <message>}). Each line is logged at WARN
 * so it surfaces in the normal server log (and whatever alerting sits on
 * top of it). Each entry is attributed to:
 * <ul>
 *   <li>the <b>account/user</b> — resolved from the Bearer token via
 *       {@link LoginFilter#getAuthenticatedUser(HttpServletRequest)};</li>
 *   <li>the <b>specific dongle</b> — the {@code User-Agent} header carries
 *       the firmware version and the stable per-device id, so a user with
 *       several dongles is still distinguishable.</li>
 * </ul>
 *
 * <p>Opt-in is the dongle's "send errors to PhoneBlock" toggle (shared with
 * crash reporting); a dongle that opted out never calls this endpoint.
 */
@WebServlet(urlPatterns = LogReportServlet.URL_PATTERN)
public class LogReportServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(LogReportServlet.class);

	public static final String URL_PATTERN = "/api/dongle/log";

	/**
	 * Hard cap on the request body. The dongle ships at most its 32-entry
	 * ring (~6 KB); anything larger is a buggy client or an abuse probe and
	 * is rejected without logging a flood.
	 */
	static final int MAX_BODY_BYTES = 16 * 1024;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			// Non-interactive Bearer-token endpoint: no WWW-Authenticate
			// challenge is appropriate (mirrors CoredumpServlet).
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		int declared = req.getContentLength();
		if (declared > MAX_BODY_BYTES) {
			resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
			return;
		}

		// Carries firmware version + stable per-device id; pins each line to
		// a specific dongle even when one user owns several.
		String agent = req.getHeader("User-Agent");
		if (agent == null || agent.isBlank()) {
			agent = "unknown";
		}

		byte[] body = readBody(req, resp);
		if (body == null) {
			// readBody already set the (413) status.
			return;
		}

		int count = 0;
		for (String line : new String(body, StandardCharsets.UTF_8).split("\n")) {
			line = line.strip();
			if (line.isEmpty()) {
				continue;
			}
			LOG.warn("Dongle error [user={}, agent={}]: {}", userName, agent, line);
			count++;
		}

		LOG.info("Received {} dongle log line(s) from user '{}' (agent {})", count, userName, agent);
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}

	/**
	 * Reads the request body into memory, enforcing {@link #MAX_BODY_BYTES}.
	 * Returns {@code null} (after setting HTTP 413 on {@code resp}) when the
	 * stream exceeds the cap.
	 */
	private static byte[] readBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try (InputStream in = req.getInputStream()) {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] chunk = new byte[4 * 1024];
			int total = 0;
			while (true) {
				int read = in.read(chunk);
				if (read < 0) {
					break;
				}
				total += read;
				if (total > MAX_BODY_BYTES) {
					resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
					return null;
				}
				buffer.write(chunk, 0, read);
			}
			return buffer.toByteArray();
		}
	}
}
