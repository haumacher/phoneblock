/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.dongle.coredump;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.jndi.JNDIProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Receives a raw ESP-IDF core dump that the PhoneBlock dongle uploads on
 * the boot following a panic. The payload is the verbatim flash region
 * written by {@code esp_core_dump_to_flash}, not an ELF — decode with
 * {@code esp-coredump info_corefile -t raw -c <file.coredump>
 * phoneblock_dongle.elf}, where the matching {@code phoneblock_dongle.elf}
 * lives in the CDN release directory.
 *
 * <p>Storage location is configured via JNDI {@code coredump/dir}. If the
 * key is unset the servlet returns 503 — the dongle treats that as
 * "keep the dump for the next boot", so flipping storage on later
 * reclaims dumps that crashed devices already carry.
 */
@WebServlet(urlPatterns = CoredumpServlet.URL_PATTERN)
public class CoredumpServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(CoredumpServlet.class);

	public static final String URL_PATTERN = "/api/dongle/coredump";

	/**
	 * Hard cap on the request body. Matches the dongle's coredump
	 * partition size (52 KB, see partitions.csv) — anything larger is
	 * either a buggy client or an abuse probe and is rejected without
	 * touching disk.
	 */
	static final int MAX_BODY_BYTES = 52 * 1024;

	private static final DateTimeFormatter TIMESTAMP =
		DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			// Not using ServletUtil.sendAuthenticationRequest here: this
			// is a non-interactive Bearer-token endpoint, no WWW-Authenticate
			// challenge is appropriate.
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		File baseDir = resolveStorageDir();
		if (baseDir == null) {
			// JNDI key unset — the dongle keeps its dump and tries on the
			// next boot, so the operator can flip storage on without
			// losing dumps already in the field.
			resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			return;
		}

		// Cheap pre-check on the declared length before we start streaming.
		int declared = req.getContentLength();
		if (declared > MAX_BODY_BYTES) {
			resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
			return;
		}

		String fw = sanitizeToken(req.getParameter("fw"), "unknown");
		String user = sanitizeToken(userName, "anonymous");
		String timestamp = TIMESTAMP.format(Instant.now());

		File versionDir = new File(baseDir, fw);
		if (!versionDir.exists() && !versionDir.mkdirs()) {
			LOG.error("Failed to create coredump directory: {}", versionDir);
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}

		File finalFile = new File(versionDir, user + "-" + timestamp + ".coredump");
		File tmpFile = new File(versionDir, finalFile.getName() + ".tmp");

		try (InputStream in = req.getInputStream();
		     BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmpFile))) {
			byte[] chunk = new byte[8 * 1024];
			int total = 0;
			while (true) {
				int read = in.read(chunk);
				if (read < 0) break;
				total += read;
				if (total > MAX_BODY_BYTES) {
					out.close();
					tmpFile.delete();
					resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
					return;
				}
				out.write(chunk, 0, read);
			}
			out.flush();
		} catch (IOException ex) {
			tmpFile.delete();
			throw ex;
		}

		// Atomic flip: only readers (a future cron, a manual analysis
		// script) should ever see the fully-written file.
		Files.move(tmpFile.toPath(), finalFile.toPath(), StandardCopyOption.ATOMIC_MOVE);

		LOG.info("Stored coredump from user '{}' for firmware '{}' ({} bytes) at {}",
			user, fw, finalFile.length(), finalFile);
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}

	/**
	 * Resolves the on-disk directory for incoming dumps. Looks up
	 * JNDI {@code coredump/dir} (with system-property fallback
	 * {@code coredump.dir} via {@link JNDIProperties}). Returns
	 * {@code null} when the property is unset, which the caller
	 * surfaces as HTTP 503.
	 */
	private static File resolveStorageDir() {
		try {
			String value = new JNDIProperties().lookupString("coredump.dir");
			if (value == null || value.isBlank()) {
				return null;
			}
			return new File(value);
		} catch (Exception ex) {
			LOG.warn("JNDI lookup for coredump/dir failed: {}", ex.getMessage());
			return null;
		}
	}

	/**
	 * Whitelist firmware version and user name to a filename-safe charset.
	 * Anything outside [A-Za-z0-9._-] becomes '_'. Empty or null falls
	 * back to {@code fallback} so we never produce an empty path segment.
	 */
	private static String sanitizeToken(String value, String fallback) {
		if (value == null) return fallback;
		StringBuilder sb = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
					|| (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
			sb.append(ok ? c : '_');
		}
		String out = sb.toString();
		if (out.isEmpty() || out.equals(".") || out.equals("..")) return fallback;
		// Hard-cap to a sane length for path-component sanity.
		if (out.length() > 64) out = out.substring(0, 64);
		return out;
	}
}
