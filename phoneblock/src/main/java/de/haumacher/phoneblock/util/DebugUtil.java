/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map.Entry;

import jakarta.servlet.http.HttpServletRequest;

import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class DebugUtil {

	public static void dumpDoc(Writer out, Document doc) {
		DOMImplementationLS ls = (DOMImplementationLS) doc.getImplementation().getFeature("LS", "3.0");
		LSOutput debug = ls.createLSOutput();
		debug.setCharacterStream(out);
		LSSerializer serializer = ls.createLSSerializer();
		serializer.write(doc, debug);
	}

	public static void dumpMethod(Writer out, HttpServletRequest req) throws IOException {
		out.write(req.getMethod() + " " + req.getPathInfo());
		out.write('\n');
	}

	public static void dumpParams(Writer out, HttpServletRequest req) throws IOException {
		for (Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
			out.write("  P: " + entry.getKey() + ": " + Arrays.asList(entry.getValue()));
			out.write('\n');
		}
	}

	public static void dumpHeaders(Writer out, HttpServletRequest req) throws IOException {
		for (Enumeration<String> keyIt = req.getHeaderNames(); keyIt.hasMoreElements(); ) {
			String key = keyIt.nextElement();
			for (Enumeration<String> valueIt = req.getHeaders(key); valueIt.hasMoreElements(); ) {
				out.write("  H: " + key + ": " + valueIt.nextElement());
				out.write('\n');
			}
		}
	}

	public static void dumpContent(Writer out, HttpServletRequest req) throws IOException {
		BufferedReader reader = req.getReader();
		String line;
		while ((line = reader.readLine()) != null) {
			out.write(line);
			out.write('\n');
		}
	}

	public static String dumpRequestInfo(HttpServletRequest req) throws IOException {
		StringWriter out = new StringWriter();
		dumpMethod(out, req);
		dumpParams(out, req);
		dumpHeaders(out, req);
		return out.toString();
	}

	public static String dumpRequestFull(HttpServletRequest req) throws IOException {
		StringWriter out = new StringWriter();
		dumpMethod(out, req);
		dumpParams(out, req);
		dumpHeaders(out, req);
		dumpContent(out, req);
		return out.toString();
	}

}
