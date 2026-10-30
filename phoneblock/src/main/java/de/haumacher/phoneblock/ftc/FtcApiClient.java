/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ftc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonToken;
import de.haumacher.msgbuf.server.io.ReaderAdapter;

/**
 * Client for the FTC Do-Not-Call complaints API at <code>api.ftc.gov</code>.
 *
 * <p>
 * The API replaces the daily CSV files formerly downloadable from <code>www.ftc.gov</code>, which
 * answers requests from outside the US with HTTP 404. It requires an api.data.gov key and returns
 * at most {@link #PAGE_SIZE} complaints per request, newest first.
 * </p>
 */
public class FtcApiClient {

	private static final String API_URL = "https://api.ftc.gov/v0/dnc-complaints";

	/** Maximum number of complaints the API returns per request. */
	public static final int PAGE_SIZE = 50;

	/** Number of days searched back for the newest complaint. */
	private static final int NEWEST_SEARCH_DAYS = 30;

	/** Format of date-time query parameters and of the <code>created-date</code> attribute. */
	static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/**
	 * One complaint record from the API.
	 */
	public record Complaint(String id, String phoneNumber, String createdDate, String subject) {}

	/**
	 * Signals that the API key's request quota is exhausted (HTTP 429).
	 */
	public static class RateLimitException extends IOException {
		RateLimitException(String message) {
			super(message);
		}
	}

	private final String _apiKey;

	/**
	 * Creates a {@link FtcApiClient}.
	 *
	 * @param apiKey the api.data.gov key.
	 */
	public FtcApiClient(String apiKey) {
		_apiKey = apiKey;
	}

	/**
	 * The creation date of the newest complaint available, or <code>null</code> if there is none in
	 * the {@link #NEWEST_SEARCH_DAYS} days before the given time.
	 *
	 * <p>
	 * The FTC uploads complaints in batches, so all complaints created before this date are
	 * already available.
	 * </p>
	 *
	 * @param now the current time (UTC). The data contains records with garbage creation dates
	 *        such as <code>20261235</code> that sort before all valid dates, so the search must be
	 *        limited to a range of valid dates.
	 */
	public LocalDateTime fetchNewestCreatedDate(LocalDateTime now) throws IOException {
		List<Complaint> page = fetch(
			"created_date_from=" + quoted(now.minusDays(NEWEST_SEARCH_DAYS)) +
			"&created_date_to=" + quoted(now) +
			"&items_per_page=" + PAGE_SIZE);
		for (Complaint complaint : page) {
			LocalDateTime created = parseCreatedDate(complaint);
			if (created != null) {
				return created;
			}
		}
		return null;
	}

	/**
	 * The creation date of the given complaint, or <code>null</code> if it is missing or invalid.
	 */
	static LocalDateTime parseCreatedDate(Complaint complaint) {
		if (complaint.createdDate() == null) {
			return null;
		}
		try {
			return LocalDateTime.parse(complaint.createdDate().strip(), DATE_TIME_FORMAT);
		} catch (DateTimeParseException ex) {
			return null;
		}
	}

	/**
	 * Fetches one page of complaints created in the given interval.
	 *
	 * @param from the first creation date to include.
	 * @param to the last creation date to include.
	 * @param offset the number of complaints to skip.
	 * @return at most {@link #PAGE_SIZE} complaints, fewer if this is the last page.
	 */
	public List<Complaint> fetchPage(LocalDateTime from, LocalDateTime to, int offset) throws IOException {
		return fetch(
			"created_date_from=" + quoted(from) +
			"&created_date_to=" + quoted(to) +
			"&items_per_page=" + PAGE_SIZE +
			"&offset=" + offset);
	}

	private static String quoted(LocalDateTime dateTime) {
		return encode("\"" + dateTime.format(DATE_TIME_FORMAT) + "\"");
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private List<Complaint> fetch(String query) throws IOException {
		String url = API_URL + "?api_key=" + encode(_apiKey) + "&" + query;

		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setConnectTimeout(30_000);
		connection.setReadTimeout(60_000);

		// Note: The URL contains the API key and must not end up in messages.
		int responseCode = connection.getResponseCode();
		if (responseCode == 429) {
			throw new RateLimitException("FTC API rate limit exceeded for query: " + query);
		}
		if (responseCode != HttpURLConnection.HTTP_OK) {
			throw new IOException("FTC API returned HTTP " + responseCode + " for query: " + query);
		}

		try (InputStream in = connection.getInputStream()) {
			return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	/**
	 * Parses an API response into its complaint records.
	 */
	static List<Complaint> parse(Reader in) throws IOException {
		List<Complaint> result = new ArrayList<>();
		try (JsonReader json = new JsonReader(new ReaderAdapter(in))) {
			json.beginObject();
			while (json.hasNext()) {
				if ("data".equals(json.nextName()) && json.peek() == JsonToken.BEGIN_ARRAY) {
					json.beginArray();
					while (json.hasNext()) {
						result.add(parseComplaint(json));
					}
					json.endArray();
				} else {
					json.skipValue();
				}
			}
			json.endObject();
		}
		return result;
	}

	private static Complaint parseComplaint(JsonReader json) throws IOException {
		String id = null;
		String phoneNumber = null;
		String createdDate = null;
		String subject = null;

		json.beginObject();
		while (json.hasNext()) {
			switch (json.nextName()) {
				case "id": id = nextStringOrNull(json); break;
				case "attributes":
					json.beginObject();
					while (json.hasNext()) {
						switch (json.nextName()) {
							case "company-phone-number": phoneNumber = nextStringOrNull(json); break;
							case "created-date": createdDate = nextStringOrNull(json); break;
							case "subject": subject = nextStringOrNull(json); break;
							default: json.skipValue();
						}
					}
					json.endObject();
					break;
				default: json.skipValue();
			}
		}
		json.endObject();

		return new Complaint(id, phoneNumber, createdDate, subject);
	}

	private static String nextStringOrNull(JsonReader json) throws IOException {
		if (json.peek() == JsonToken.NULL) {
			json.nextNull();
			return null;
		}
		return json.nextString();
	}
}
