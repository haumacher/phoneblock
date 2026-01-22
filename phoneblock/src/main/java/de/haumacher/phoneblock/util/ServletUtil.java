/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.msgbuf.data.DataObject;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.msgbuf.xml.XmlSerializable;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.DBUserSettings;
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
		String dialPrefix;
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			dialPrefix = LocationService.getInstance().getDialPrefix(req);
		} else {
			DB db = DBService.getInstance();
			try (SqlSession session = db.openSession()) {
				DBUserSettings settings = db.getUserSettingsRaw(session, userName);
				dialPrefix = settings.getDialPrefix();
			}
		}
		return dialPrefix;
	}

}
