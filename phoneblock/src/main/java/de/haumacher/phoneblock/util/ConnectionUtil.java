/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLConnection;

/**
 * Utilities for connection handling.
 */
public class ConnectionUtil {

	/**
	 * Reads the contents of a {@link HttpURLConnection} as text.
	 */
	public static StringBuilder readText(URLConnection connection) throws IOException, UnsupportedEncodingException {
		String encoding = connection.getContentEncoding();
		
		try (InputStream in = connection.getInputStream()) {
			return readText(in, encoding);
		}
	}

	/**
	 * Reads the contents of a {@link InputStream} as text.
	 */
	public static StringBuilder readText(InputStream in, String encoding) throws IOException, UnsupportedEncodingException {
		StringBuilder contents = new StringBuilder();
		char[] buffer = new char[4096];
		if (encoding == null) {
			encoding = "utf-8";
		}
		try (Reader r = new InputStreamReader(in, encoding)) {
			while (true) {
				int direct = r.read(buffer);
				if (direct < 0) {
					break;
				}
				contents.append(buffer, 0, direct);
			}
		}
		return contents;
	}

}
