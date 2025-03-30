/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import java.io.IOException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

import de.haumacher.msgbuf.data.DataObject;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.msgbuf.xml.XmlSerializable;
import jakarta.servlet.ServletException;
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

}
