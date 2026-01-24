package de.haumacher.phoneblock.sync.client;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * HTTP client for communicating with the PhoneBlock blocklist API.
 */
public class BlocklistClient {
	private static final Logger LOG = LoggerFactory.getLogger(BlocklistClient.class);

	private final String apiUrl;
	private final String bearerToken;
	private final HttpClient httpClient;

	/**
	 * Creates a blocklist API client.
	 *
	 * @param apiUrl The base API URL (e.g., "https://phoneblock.net/phoneblock/api/blocklist").
	 * @param bearerToken The bearer token for authentication.
	 */
	public BlocklistClient(String apiUrl, String bearerToken) {
		this(apiUrl, bearerToken, 30);
	}

	/**
	 * Creates a blocklist API client with custom timeout.
	 *
	 * @param apiUrl The base API URL.
	 * @param bearerToken The bearer token for authentication.
	 * @param timeoutSeconds Connection timeout in seconds.
	 */
	public BlocklistClient(String apiUrl, String bearerToken, int timeoutSeconds) {
		this.apiUrl = apiUrl;
		this.bearerToken = bearerToken;
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(timeoutSeconds))
			.build();
	}

	/**
	 * Downloads the full blocklist.
	 *
	 * @return The complete blocklist.
	 * @throws IOException If the download fails.
	 */
	public Blocklist downloadFullBlocklist() throws IOException {
		LOG.info("Downloading full blocklist from: {}", apiUrl);
		return fetch(apiUrl);
	}

	/**
	 * Downloads incremental updates since the given version.
	 *
	 * @param sinceVersion The version to sync from.
	 * @return The incremental update.
	 * @throws IOException If the download fails.
	 */
	public Blocklist downloadIncrementalUpdate(long sinceVersion) throws IOException {
		String url = apiUrl + "?since=" + sinceVersion;
		LOG.info("Downloading incremental update from version {} (URL: {})", sinceVersion, url);
		return fetch(url);
	}

	private Blocklist fetch(String url) throws IOException {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.header("Authorization", "Bearer " + bearerToken)
			.header("User-Agent", "PhoneBlockSync/1.0")
			.GET()
			.build();

		try {
			HttpResponse<InputStream> response = httpClient.send(request,
				HttpResponse.BodyHandlers.ofInputStream());

			int statusCode = response.statusCode();
			LOG.debug("HTTP response status: {}", statusCode);

			if (statusCode == 401 || statusCode == 403) {
				throw new IOException("Authentication failed: invalid bearer token (HTTP " + statusCode + ")");
			}
			if (statusCode != 200) {
				throw new IOException("HTTP request failed with status " + statusCode);
			}

			try (InputStream in = response.body();
			     JsonReader reader = new JsonReader(new ReaderAdapter(new InputStreamReader(in, UTF_8)))) {
				Blocklist blocklist = Blocklist.readBlocklist(reader);
				LOG.info("Downloaded blocklist version {} with {} entries",
					blocklist.getVersion(), blocklist.getNumbers().size());
				return blocklist;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("HTTP request interrupted", e);
		}
	}
}
