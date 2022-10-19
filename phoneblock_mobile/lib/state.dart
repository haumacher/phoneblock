import 'package:jsontool/jsontool.dart';

/// Common functionality for JSON generation and parsing.
abstract class _JsonObject {
	@override
	String toString() {
		var buffer = StringBuffer();
		writeTo(jsonStringWriter(buffer));
		return buffer.toString();
	}

	/// The ID to announce the type of the object.
	String _jsonType();

	/// Reads the object contents (after the type information).
	void _readContent(JsonReader json) {
		json.expectObject();
		while (json.hasNextKey()) {
			var key = json.nextKey();
			_readProperty(key!, json);
		}
	}

	/// Reads the value of the property with the given name.
	void _readProperty(String key, JsonReader json) {
		json.skipAnyValue();
	}

	/// Writes this object to the given writer (including type information).
	void writeTo(JsonSink json) {
		json.startArray();
		json.addString(_jsonType());
		writeContent(json);
		json.endArray();
	}

	/// Writes the contents of this object to the given writer (excluding type information).
	void writeContent(JsonSink json) {
		json.startObject();
		_writeProperties(json);
		json.endObject();
	}

	/// Writes all key/value pairs of this object.
	void _writeProperties(JsonSink json) {
		// No properties.
	}
}

class AppState extends _JsonObject {
	List<Call> calls;

	/// Creates a AppState.
	AppState({
			this.calls = const [], 
	});

	/// Parses a AppState from a string source.
	static AppState? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a AppState instance from the given reader.
	static AppState read(JsonReader json) {
		AppState result = AppState();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "AppState";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "calls": {
				json.expectArray();
				calls = [];
				while (json.hasNext()) {
					if (!json.tryNull()) {
						calls.add(Call.read(json));
					}
				}
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("calls");
		json.startArray();
		for (var _element in calls) {
			_element.writeContent(json);
		}
		json.endArray();
	}

}

class Call extends _JsonObject {
	///  The direction of the call.
	Type type;

	///  A rating already assigned to this call.
	Rating rating;

	///  The phone number of the called (or calling) party.
	String phone;

	///  The name of the counterpart from the address book.
	String? label;

	///  Timestamp when this call has started.
	int started;

	///  Duration of the call in milliseconds.
	int duration;

	/// Creates a Call.
	Call({
			this.type = Type.mISSED, 
			this.rating = Rating.aLEGITIMATE, 
			this.phone = "", 
			this.label, 
			this.started = 0, 
			this.duration = 0, 
	});

	/// Parses a Call from a string source.
	static Call? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a Call instance from the given reader.
	static Call read(JsonReader json) {
		Call result = Call();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "Call";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "type": {
				type = readType(json);
				break;
			}
			case "rating": {
				rating = readRating(json);
				break;
			}
			case "phone": {
				phone = json.expectString();
				break;
			}
			case "label": {
				label = json.expectString();
				break;
			}
			case "started": {
				started = json.expectInt();
				break;
			}
			case "duration": {
				duration = json.expectInt();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("type");
		writeType(json, type);

		json.addKey("rating");
		writeRating(json, rating);

		json.addKey("phone");
		json.addString(phone);

		var _label = label;
		if (_label != null) {
			json.addKey("label");
			json.addString(_label);
		}

		json.addKey("started");
		json.addNumber(started);

		json.addKey("duration");
		json.addNumber(duration);
	}

}

enum Type {
	mISSED,
	bLOCKED,
	iNCOMING,
	oUTGOING,
}

/// Writes a value of Type to a JSON stream.
void writeType(JsonSink json, Type value) {
	switch (value) {
		case Type.mISSED: json.addString("MISSED"); break;
		case Type.bLOCKED: json.addString("BLOCKED"); break;
		case Type.iNCOMING: json.addString("INCOMING"); break;
		case Type.oUTGOING: json.addString("OUTGOING"); break;
		default: throw ("No such literal: " + value.name);
	}
}

/// Reads a value of Type from a JSON stream.
Type readType(JsonReader json) {
	switch (json.expectString()) {
		case "MISSED": return Type.mISSED;
		case "BLOCKED": return Type.bLOCKED;
		case "INCOMING": return Type.iNCOMING;
		case "OUTGOING": return Type.oUTGOING;
		default: return Type.mISSED;
	}
}

///  Message to request an update of the blocklist from the PhoneBlock server.
class GetReports extends _JsonObject {
	///  All changes to the blocklist starting with this timestamp are requested.
	int notBefore;

	/// Creates a GetReports.
	GetReports({
			this.notBefore = 0, 
	});

	/// Parses a GetReports from a string source.
	static GetReports? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a GetReports instance from the given reader.
	static GetReports read(JsonReader json) {
		GetReports result = GetReports();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "GetReports";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "notBefore": {
				notBefore = json.expectInt();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("notBefore");
		json.addNumber(notBefore);
	}

}

///  Result message of the {@link #GetReports} request.
class Reports extends _JsonObject {
	///  The timestamp to set in the next {@link GetReports} request in the {@link GetReports#notBefore} field to request the next update.
	int revision;

	///  All new spam reports.
	List<Report> reports;

	/// Creates a Reports.
	Reports({
			this.revision = 0, 
			this.reports = const [], 
	});

	/// Parses a Reports from a string source.
	static Reports? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a Reports instance from the given reader.
	static Reports read(JsonReader json) {
		Reports result = Reports();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "Reports";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "revision": {
				revision = json.expectInt();
				break;
			}
			case "reports": {
				json.expectArray();
				reports = [];
				while (json.hasNext()) {
					if (!json.tryNull()) {
						reports.add(Report.read(json));
					}
				}
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("revision");
		json.addNumber(revision);

		json.addKey("reports");
		json.startArray();
		for (var _element in reports) {
			_element.writeContent(json);
		}
		json.endArray();
	}

}

///  A single report in the {@link Reports} response.
class Report extends _JsonObject {
	///  The phone number that is a source of spam calls.
	String phone;

	///  The kind of spam that comes from the {@link #phone number}.
	Rating rating;

	///  The number of votes that reported the {@link #phone number} as spam.
	int votes;

	///  When the last update for the {@link #phone number} was reported to the PhoneBlock server.
	int lastUpdate;

	/// Creates a Report.
	Report({
			this.phone = "", 
			this.rating = Rating.aLEGITIMATE, 
			this.votes = 0, 
			this.lastUpdate = 0, 
	});

	/// Parses a Report from a string source.
	static Report? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a Report instance from the given reader.
	static Report read(JsonReader json) {
		Report result = Report();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "Report";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "phone": {
				phone = json.expectString();
				break;
			}
			case "rating": {
				rating = readRating(json);
				break;
			}
			case "votes": {
				votes = json.expectInt();
				break;
			}
			case "lastUpdate": {
				lastUpdate = json.expectInt();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("phone");
		json.addString(phone);

		json.addKey("rating");
		writeRating(json, rating);

		json.addKey("votes");
		json.addNumber(votes);

		json.addKey("lastUpdate");
		json.addNumber(lastUpdate);
	}

}

///  A classification of spam phone calls.
enum Rating {
	///  A regular non-spam call.
	aLEGITIMATE,
	///  The user has missed the call and cannot decide, whether it was spam.
	uNKNOWN,
	///  The caller immediately cut the connection.
	pING,
	///  A poll.
	pOLL,
	///  Some form of advertising, marketing unwanted consulting.
	aDVERTISING,
	///  Some form of gambling or notice of prize notification.
	gAMBLE,
	///  Some form of fraud.
	fRAUD,
}

/// Writes a value of Rating to a JSON stream.
void writeRating(JsonSink json, Rating value) {
	switch (value) {
		case Rating.aLEGITIMATE: json.addString("A_LEGITIMATE"); break;
		case Rating.uNKNOWN: json.addString("UNKNOWN"); break;
		case Rating.pING: json.addString("PING"); break;
		case Rating.pOLL: json.addString("POLL"); break;
		case Rating.aDVERTISING: json.addString("ADVERTISING"); break;
		case Rating.gAMBLE: json.addString("GAMBLE"); break;
		case Rating.fRAUD: json.addString("FRAUD"); break;
		default: throw ("No such literal: " + value.name);
	}
}

/// Reads a value of Rating from a JSON stream.
Rating readRating(JsonReader json) {
	switch (json.expectString()) {
		case "A_LEGITIMATE": return Rating.aLEGITIMATE;
		case "UNKNOWN": return Rating.uNKNOWN;
		case "PING": return Rating.pING;
		case "POLL": return Rating.pOLL;
		case "ADVERTISING": return Rating.aDVERTISING;
		case "GAMBLE": return Rating.gAMBLE;
		case "FRAUD": return Rating.fRAUD;
		default: return Rating.aLEGITIMATE;
	}
}

