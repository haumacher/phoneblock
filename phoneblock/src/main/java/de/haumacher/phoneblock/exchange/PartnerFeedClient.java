/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.exchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Fetches a window of a partner's report feed.
 *
 * <p>
 * The seam between {@link NizeImportService} and the network, so that tests can feed fixtures.
 * </p>
 */
public interface PartnerFeedClient {

	/**
	 * Fetches the partner reports created in <code>[from, to)</code>.
	 *
	 * @param from Start of the window in milliseconds since epoch (UTC).
	 * @param to End of the window in milliseconds since epoch (UTC).
	 * @return The parsed feed.
	 * @throws IOException If the partner cannot be reached or does not answer with HTTP 200.
	 */
	PartnerReport.Feed fetch(long from, long to) throws IOException;

	/**
	 * A {@link PartnerFeedClient} fetching a feed from an HTTP URL with bearer token authentication.
	 *
	 * @param url The feed URL, the window is appended as <code>from</code> and <code>to</code> query
	 *        parameters.
	 * @param token The bearer token.
	 */
	static PartnerFeedClient http(String url, String token) {
		return (from, to) -> {
			String requestUrl = url + (url.contains("?") ? "&" : "?") + "from=" + from + "&to=" + to;

			HttpURLConnection connection = (HttpURLConnection) URI.create(requestUrl).toURL().openConnection();
			connection.setConnectTimeout(30_000);
			connection.setReadTimeout(60_000);
			connection.setRequestProperty("Authorization", "Bearer " + token);
			connection.setRequestProperty("Accept", "application/json");

			int responseCode = connection.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				throw new IOException("Partner feed returned HTTP " + responseCode + " for: " + requestUrl);
			}

			try (InputStream in = connection.getInputStream()) {
				return PartnerReport.parseFeed(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
		};
	}
}
