/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

import de.haumacher.msgbuf.data.DataObject;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.msgbuf.xml.XmlSerializable;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.location.LocationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Utility methods for servlet implementations.
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class ServletUtil {

	private static final String CURRENT_PAGE = "currentPage";

	/** 
	 * Send an authentication request.
	 */
	public static void sendAuthenticationRequest(HttpServletResponse resp) throws IOException {
		resp.setHeader("WWW-Authenticate", "Basic realm=\"PhoneBlock\", charset=\"UTF-8\"");
		sendMessage(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please provide login credentials.");
	}

	/** 
	 * Marshals the result back to the client.
	 */
	public static <T extends DataObject & XmlSerializable> void sendResult(HttpServletRequest req, HttpServletResponse resp, T result)
			throws IOException {
		if (sendXml(req)) {
			resp.setContentType("text/xml");
			resp.setCharacterEncoding("utf-8");
			
			try {
				result.writeTo(XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(resp.getWriter()));
			} catch (XMLStreamException ex) {
				throw new IOException("Writing XML response failed.", ex);
			}
		} else {
			resp.setContentType("application/json");
			resp.setCharacterEncoding("utf-8");
			
			result.writeTo(new JsonWriter(new WriterAdapter(resp.getWriter())));
		}
	}

	private static boolean sendXml(HttpServletRequest req) {
		String format = req.getParameter("format");
		if (format != null && format.equals("xml")) {
			return true;
		}
		
		String accept = req.getHeader("Accept");
		return accept != null && accept.endsWith("/xml");
	}

	public static void sendError(HttpServletResponse resp, String message) throws IOException {
		ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, message);
	}

	/** HTTP 429 — not defined as a constant in the servlet API. */
	public static final int SC_TOO_MANY_REQUESTS = 429;

	/**
	 * Enforces the per-subject rate limit for an expensive API call.
	 *
	 * <p>The limit is keyed on the authenticated API token when one is present
	 * (each API key has its own budget), and on the user account otherwise —
	 * covering CardDAV access authenticated by account password, which carries
	 * no persistent token. Unauthenticated callers are not limited here (e.g. the
	 * public number lookup), since there is no subject to attribute usage to.</p>
	 *
	 * @param bucket   one of the {@code DB.QUOTA_BUCKET_*} constants.
	 * @param interval window length in milliseconds.
	 * @param limit    maximum number of requests per window.
	 * @return {@code true} if the call is within quota and may proceed;
	 *         {@code false} if the limit was exceeded — in which case a 429
	 *         response with a {@code Retry-After} header has already been written
	 *         and the caller must abort.
	 */
	public static boolean enforceQuota(HttpServletRequest req, HttpServletResponse resp, int bucket, long interval, int limit) throws IOException {
		AuthToken auth = LoginFilter.getAuthorization(req);
		if (auth == null) {
			return true;
		}

		long retryAfter = DBService.getInstance().tryConsumeQuota(auth, bucket, System.currentTimeMillis(), interval, limit);
		if (retryAfter < 0) {
			return true;
		}

		resp.setHeader("Retry-After", Long.toString(retryAfter));
		sendMessage(resp, SC_TOO_MANY_REQUESTS,
			"Rate limit exceeded for this " + (auth.getId() > 0 ? "API key" : "account")
				+ ". Retry after " + retryAfter + " seconds.");
		return false;
	}

	public static void sendMessage(HttpServletResponse resp, int status, String message) throws IOException {
		resp.setStatus(status);
		sendText(resp, message);
	}

	public static void sendText(HttpServletResponse resp, String message) throws IOException {
		resp.setContentType("text/plain");
		resp.setCharacterEncoding("utf-8");
		resp.getWriter().write(message);
	}

	public static String currentPage(HttpServletRequest req) {
		String currentPage = (String) req.getAttribute(CURRENT_PAGE);
		if (currentPage == null) {
			String requestURI = req.getRequestURI();
			currentPage = requestURI.substring(req.getContextPath().length());
		}
		return currentPage;
	}

	public static String forwardParam(String url, HttpServletRequest req, String ...params) {
		for (String param : params) {
			url = forwardParam(url, req, param);
		}
		return url;
	}
	
	public static String forwardParam(String url, HttpServletRequest req, String param) {
		String value = req.getParameter(param);
		return withParam(url, param, value);
	}
	
	public static String withParam(String url, String param, String tokenLabel) {
		if (tokenLabel == null || tokenLabel.trim().isEmpty()) {
			return url;
		}
		String separator = url.indexOf('?') < 0 ? "?" : "&";
		return url += separator + param + "=" + URLEncoder.encode(tokenLabel, StandardCharsets.UTF_8);
	}

	public static void declareAttribute(HttpServletRequest req, String param) {
		String tokenLabel = req.getParameter(param);
		if (tokenLabel != null && !tokenLabel.trim().isEmpty()) {
			req.setAttribute(param, tokenLabel);
		}
	}

	public static String lookupDialPrefix(HttpServletRequest req) {
		UserSettings settings = LoginFilter.getUserSettings(req);
		if (settings != null) {
			return settings.getDialPrefix();
		}
		return LocationService.getInstance().browserDialPrefix(req);
	}

}
