/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.dongle.pairing;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Generates the per-install "pairing" partition image that the browser-side
 * flasher writes onto fresh dongles at offset 0x12000.
 *
 * <p>The 16-byte secret is supplied by the install page (which mints a fresh
 * one via {@code crypto.getRandomValues()} per session). Validation here is
 * purely format: 32 lowercase-hex characters. The secret only becomes
 * meaningful once the dongle posts it back via {@code /api/dongle/register}
 * (added in a follow-up commit).
 */
@WebServlet(urlPatterns = "/dongle/pairing.bin")
public class PairingBinServlet extends HttpServlet {

	private static final Pattern HEX32 = Pattern.compile("[0-9a-f]{32}");

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		String secretHex = req.getParameter("secret");
		if (secretHex == null || !HEX32.matcher(secretHex).matches()) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.setContentType("text/plain;charset=utf-8");
			resp.getWriter().write("Missing or malformed 'secret' (expect 32 lowercase hex chars).");
			return;
		}

		byte[] secret = decodeHex(secretHex);
		byte[] image  = PairingPartition.build(secret);

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/octet-stream");
		resp.setContentLength(image.length);
		// Per-session, never cache: the secret must not leak between users
		// via shared caches (browser, CDN, corporate proxy).
		resp.setHeader("Cache-Control", "no-store");
		resp.getOutputStream().write(image);
	}

	private static byte[] decodeHex(String hex) {
		byte[] out = new byte[hex.length() / 2];
		for (int i = 0; i < out.length; i++) {
			int hi = Character.digit(hex.charAt(i * 2),     16);
			int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
			out[i] = (byte) ((hi << 4) | lo);
		}
		return out;
	}
}
