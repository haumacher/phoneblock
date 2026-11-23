/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.exchange;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonToken;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;

/**
 * One spam report of the partner report exchange, in the shape of the partner's feed
 * (<code>{"comments": [{"id", "phone", "senderId", "created", "text", "score", "name", "typeId"}]}</code>).
 *
 * <p>
 * Optional fields are <code>null</code> when absent. The partner's <code>name</code> field (the
 * caller name entered by the reporter) is neither read nor written.
 * </p>
 *
 * @param id Stable report ID, used for deduplication.
 * @param phone The phone number in E.164 format.
 * @param senderId Stable anonymized reporter ID.
 * @param created Creation time in milliseconds since epoch (UTC).
 * @param text The report comment, optional.
 * @param score 1 (spam) to 5 (trustworthy), optional.
 * @param typeId The report category, optional.
 */
public record PartnerReport(String id, String phone, String senderId, Long created, String text, Integer score,
		Integer typeId) {

	/** Name of the top-level array in the feed. */
	static final String COMMENTS = "comments";

	/** Name of the top-level error object a feed may contain instead of (or in addition to) data. */
	static final String ERROR = "error";

	/**
	 * A parsed partner feed.
	 *
	 * @param reports The reports of the feed.
	 * @param error Whether the feed contained a (non-null) <code>"error"</code> field.
	 */
	public record Feed(List<PartnerReport> reports, boolean error) {}

	/**
	 * Writes the given reports as feed document.
	 */
	public static void writeFeed(JsonWriter json, List<PartnerReport> reports) throws IOException {
		json.beginObject();
		json.name(COMMENTS);
		json.beginArray();
		for (PartnerReport report : reports) {
			report.write(json);
		}
		json.endArray();
		json.endObject();
	}

	/**
	 * Writes this report as JSON object, leaving out absent optional fields.
	 */
	public void write(JsonWriter json) throws IOException {
		json.beginObject();
		json.name("id").value(id);
		json.name("phone").value(phone);
		json.name("senderId").value(senderId);
		if (created != null) {
			json.name("created").value(created.longValue());
		}
		if (text != null) {
			json.name("text").value(text);
		}
		if (score != null) {
			json.name("score").value(score.intValue());
		}
		if (typeId != null) {
			json.name("typeId").value(typeId.intValue());
		}
		json.endObject();
	}

	/**
	 * Parses a feed document.
	 */
	public static Feed parseFeed(Reader in) throws IOException {
		List<PartnerReport> reports = new ArrayList<>();
		boolean error = false;
		try (JsonReader json = new JsonReader(new ReaderAdapter(in))) {
			json.beginObject();
			while (json.hasNext()) {
				String name = json.nextName();
				if (COMMENTS.equals(name) && json.peek() == JsonToken.BEGIN_ARRAY) {
					json.beginArray();
					while (json.hasNext()) {
						reports.add(parseReport(json));
					}
					json.endArray();
				} else {
					if (ERROR.equals(name) && json.peek() != JsonToken.NULL) {
						error = true;
					}
					json.skipValue();
				}
			}
			json.endObject();
		}
		return new Feed(reports, error);
	}

	private static PartnerReport parseReport(JsonReader json) throws IOException {
		String id = null;
		String phone = null;
		String senderId = null;
		Long created = null;
		String text = null;
		Integer score = null;
		Integer typeId = null;

		json.beginObject();
		while (json.hasNext()) {
			switch (json.nextName()) {
				case "id": id = nextString(json); break;
				case "phone": phone = nextString(json); break;
				case "senderId": senderId = nextString(json); break;
				case "created": created = nextLong(json); break;
				case "text": text = nextString(json); break;
				case "score": score = nextInt(json); break;
				case "typeId": typeId = nextInt(json); break;
				default: json.skipValue();
			}
		}
		json.endObject();

		return new PartnerReport(id, phone, senderId, created, text, score, typeId);
	}

	private static String nextString(JsonReader json) throws IOException {
		if (json.peek() == JsonToken.NULL) {
			json.nextNull();
			return null;
		}
		return json.nextString();
	}

	private static Long nextLong(JsonReader json) throws IOException {
		String value = nextString(json);
		if (value == null) {
			return null;
		}
		value = value.strip();
		try {
			return Long.valueOf(value);
		} catch (NumberFormatException ex) {
			// E.g. "1.0".
			try {
				return Long.valueOf((long) Double.parseDouble(value));
			} catch (NumberFormatException ex2) {
				return null;
			}
		}
	}

	private static Integer nextInt(JsonReader json) throws IOException {
		Long value = nextLong(json);
		return value == null ? null : Integer.valueOf(value.intValue());
	}

}
