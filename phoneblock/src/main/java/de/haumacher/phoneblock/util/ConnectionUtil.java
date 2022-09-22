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

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class ConnectionUtil {

	public static StringBuilder readText(HttpURLConnection connection) throws IOException, UnsupportedEncodingException {
		try (InputStream in = connection.getInputStream()) {
			String encoding = connection.getContentEncoding();
			
			return readText(in, encoding);
		}
	}

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
