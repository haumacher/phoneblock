/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.dongle.pairing;

import java.io.IOException;

import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Endpoint the install page polls after the flash completes. Returns the
 * LAN IP previously registered under the same {@code secret} so the page
 * can navigate the user's browser to the dongle.
 */
@WebServlet(urlPatterns = "/api/dongle/lookup")
public class LookupDongleServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		PairingRegistry registry = PairingRegistry.getInstance();
		if (registry == null) {
			resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			return;
		}

		String secret = req.getParameter("secret");
		String lanIp  = registry.lookup(secret);
		if (lanIp == null) {
			// Same response shape for "unknown" and "expired" — both look
			// like "still waiting" to the polling browser.
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		LookupResponse response = LookupResponse.create().setLanIp(lanIp);
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("utf-8");
		resp.setHeader("Cache-Control", "no-store");
		response.writeTo(new JsonWriter(new WriterAdapter(resp.getWriter())));
	}
}
