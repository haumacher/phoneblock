/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

import de.haumacher.msgbuf.data.DataObject;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.msgbuf.xml.XmlSerializable;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DBService;

/**
 * Utility methods for servlet implementations.
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class ServletUtil {

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

	public static boolean checkAuthentication(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String authHeader = req.getHeader("Authorization");
		if (authHeader != null && !authHeader.isEmpty()) {
			String userName = DBService.getInstance().basicAuth(authHeader);
			if (userName != null) {
				req.setAttribute(LoginFilter.AUTHENTICATED_USER_ATTR, userName);
				return true;
			}
		}
		
		sendAuthenticationRequest(resp);
		return false;
	}

}
