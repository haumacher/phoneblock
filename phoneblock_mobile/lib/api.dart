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

///  Information that must be requested to start a registration process.
class RegistrationChallenge extends _JsonObject {
	///  The registration session ID, must be provided to following calls.
	String session;

	///  A Base64 encoded image hiding some random text. The text must be entered to the {@link RegistrationRequest#answer} field.
	String captcha;

	/// Creates a RegistrationChallenge.
	RegistrationChallenge({
			this.session = "", 
			this.captcha = "", 
	});

	/// Parses a RegistrationChallenge from a string source.
	static RegistrationChallenge? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a RegistrationChallenge instance from the given reader.
	static RegistrationChallenge read(JsonReader json) {
		RegistrationChallenge result = RegistrationChallenge();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "RegistrationChallenge";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "session": {
				session = json.expectString();
				break;
			}
			case "captcha": {
				captcha = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("session");
		json.addString(session);

		json.addKey("captcha");
		json.addString(captcha);
	}

}

///  Requesting a new user account.
class RegistrationRequest extends _JsonObject {
	///  The registration session ID given in {@link RegistrationChallenge#session}.
	String session;

	///  The decoded captcha text from {@link RegistrationChallenge#captcha}.
	String answer;

	///  The e-mail address of the user to register
	String email;

	/// Creates a RegistrationRequest.
	RegistrationRequest({
			this.session = "", 
			this.answer = "", 
			this.email = "", 
	});

	/// Parses a RegistrationRequest from a string source.
	static RegistrationRequest? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a RegistrationRequest instance from the given reader.
	static RegistrationRequest read(JsonReader json) {
		RegistrationRequest result = RegistrationRequest();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "RegistrationRequest";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "session": {
				session = json.expectString();
				break;
			}
			case "answer": {
				answer = json.expectString();
				break;
			}
			case "email": {
				email = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("session");
		json.addString(session);

		json.addKey("answer");
		json.addString(answer);

		json.addKey("email");
		json.addString(email);
	}

}

///  The completion of the registration.
class RegistrationCompletion extends _JsonObject {
	///  The registration session ID given in {@link RegistrationChallenge#session}.
	String session;

	///  The code that was sent to the user's e-mail address.
	String code;

	/// Creates a RegistrationCompletion.
	RegistrationCompletion({
			this.session = "", 
			this.code = "", 
	});

	/// Parses a RegistrationCompletion from a string source.
	static RegistrationCompletion? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a RegistrationCompletion instance from the given reader.
	static RegistrationCompletion read(JsonReader json) {
		RegistrationCompletion result = RegistrationCompletion();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "RegistrationCompletion";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "session": {
				session = json.expectString();
				break;
			}
			case "code": {
				code = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("session");
		json.addString(session);

		json.addKey("code");
		json.addString(code);
	}

}

///  The login data created during registration.
class RegistrationResult extends _JsonObject {
	///  The registration session ID given in {@link RegistrationChallenge#session}.
	String session;

	///  The new user name.
	String login;

	///  The user's secure password.
	String password;

	/// Creates a RegistrationResult.
	RegistrationResult({
			this.session = "", 
			this.login = "", 
			this.password = "", 
	});

	/// Parses a RegistrationResult from a string source.
	static RegistrationResult? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a RegistrationResult instance from the given reader.
	static RegistrationResult read(JsonReader json) {
		RegistrationResult result = RegistrationResult();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "RegistrationResult";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "session": {
				session = json.expectString();
				break;
			}
			case "login": {
				login = json.expectString();
				break;
			}
			case "password": {
				password = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("session");
		json.addString(session);

		json.addKey("login");
		json.addString(login);

		json.addKey("password");
		json.addString(password);
	}

}

///  Internal data that is kept between registration requests on the server.
class SessionInfo extends _JsonObject {
	///  Time when the registration process was started by retrieving the {@link RegistrationChallenge}.
	int created;

	///  The registration session ID given in {@link RegistrationChallenge#session}.
	String session;

	///  The e-mail address of the user to register
	String email;

	///  The expected answer to the captcha.
	String answer;

	///  The code that was sent to the user's e-mail address.
	String code;

	/// Creates a SessionInfo.
	SessionInfo({
			this.created = 0, 
			this.session = "", 
			this.email = "", 
			this.answer = "", 
			this.code = "", 
	});

	/// Parses a SessionInfo from a string source.
	static SessionInfo? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a SessionInfo instance from the given reader.
	static SessionInfo read(JsonReader json) {
		SessionInfo result = SessionInfo();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "SessionInfo";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "created": {
				created = json.expectInt();
				break;
			}
			case "session": {
				session = json.expectString();
				break;
			}
			case "email": {
				email = json.expectString();
				break;
			}
			case "answer": {
				answer = json.expectString();
				break;
			}
			case "code": {
				code = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("created");
		json.addNumber(created);

		json.addKey("session");
		json.addString(session);

		json.addKey("email");
		json.addString(email);

		json.addKey("answer");
		json.addString(answer);

		json.addKey("code");
		json.addString(code);
	}

}

///  A classification of phone calls.
enum Rating {
	///  A regular non-spam call.
	aLegitimate,
	///  The call is negatively rated without determining the call type. For legacy reasons, this constant is named "missed".
	bMissed,
	///  The caller immediately cut the connection.
	cPing,
	///  A poll.
	dPoll,
	///  Some form of advertising, marketing unwanted consulting.
	eAdvertising,
	///  Some form of gambling or notice of prize notification.
	fGamble,
	///  Some form of fraud.
	gFraud,
}

/// Writes a value of Rating to a JSON stream.
void writeRating(JsonSink json, Rating value) {
	switch (value) {
		case Rating.aLegitimate: json.addString("A_LEGITIMATE"); break;
		case Rating.bMissed: json.addString("B_MISSED"); break;
		case Rating.cPing: json.addString("C_PING"); break;
		case Rating.dPoll: json.addString("D_POLL"); break;
		case Rating.eAdvertising: json.addString("E_ADVERTISING"); break;
		case Rating.fGamble: json.addString("F_GAMBLE"); break;
		case Rating.gFraud: json.addString("G_FRAUD"); break;
		default: throw ("No such literal: " + value.name);
	}
}

/// Reads a value of Rating from a JSON stream.
Rating readRating(JsonReader json) {
	switch (json.expectString()) {
		case "A_LEGITIMATE": return Rating.aLegitimate;
		case "B_MISSED": return Rating.bMissed;
		case "C_PING": return Rating.cPing;
		case "D_POLL": return Rating.dPoll;
		case "E_ADVERTISING": return Rating.eAdvertising;
		case "F_GAMBLE": return Rating.fGamble;
		case "G_FRAUD": return Rating.gFraud;
		default: return Rating.aLegitimate;
	}
}

///  Request to a add a new rating for a phone number.
class RateRequest extends _JsonObject {
	///  The phone number to rate.
	String phone;

	///  The rating code. Must be one of the the codes defined in {@link de.haumacher.phoneblock.db.model.Rating}.
	Rating rating;

	///  A user comment describing the call or owner of the phone number.
	String comment;

	/// Creates a RateRequest.
	RateRequest({
			this.phone = "", 
			this.rating = Rating.aLegitimate, 
			this.comment = "", 
	});

	/// Parses a RateRequest from a string source.
	static RateRequest? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a RateRequest instance from the given reader.
	static RateRequest read(JsonReader json) {
		RateRequest result = RateRequest();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "RateRequest";

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
			case "comment": {
				comment = json.expectString();
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

		json.addKey("comment");
		json.addString(comment);
	}

}

///  Information about a phone number that is published to the <i>PhoneBlock API</i>.
class PhoneInfo extends _JsonObject {
	///  The number being requested.
	String phone;

	///  The number of votes that support blocking the requested number.
	int votes;

	///  The rating for the requested phone number.
	Rating rating;

	///  The number of votes when also considering votes for numbers that have all but the last two digits in common with the requested number.
	/// 
	///  <p>
	///  Votes to those near-by numbers are only considered, when the density of SPAM numbers around the requested number is found to be high.
	///  </p>
	int votesWildcard;

	///  Whether this number is on the white list and therefore cannot receive votes.
	bool whiteListed;

	///  Whether this number no longer is on the blocklist, because no votes have been received for a long time.
	bool archived;

	///  Date when this number was added to the SPAM database (in milliseconds since epoch).
	int dateAdded;

	///  Date when the last report for this number was received (in milliseconds since epoch).
	int lastUpdate;

	/// Creates a PhoneInfo.
	PhoneInfo({
			this.phone = "", 
			this.votes = 0, 
			this.rating = Rating.aLegitimate, 
			this.votesWildcard = 0, 
			this.whiteListed = false, 
			this.archived = false, 
			this.dateAdded = 0, 
			this.lastUpdate = 0, 
	});

	/// Parses a PhoneInfo from a string source.
	static PhoneInfo? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a PhoneInfo instance from the given reader.
	static PhoneInfo read(JsonReader json) {
		PhoneInfo result = PhoneInfo();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "PhoneInfo";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "phone": {
				phone = json.expectString();
				break;
			}
			case "votes": {
				votes = json.expectInt();
				break;
			}
			case "rating": {
				rating = readRating(json);
				break;
			}
			case "votesWildcard": {
				votesWildcard = json.expectInt();
				break;
			}
			case "whiteListed": {
				whiteListed = json.expectBool();
				break;
			}
			case "archived": {
				archived = json.expectBool();
				break;
			}
			case "dateAdded": {
				dateAdded = json.expectInt();
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

		json.addKey("votes");
		json.addNumber(votes);

		json.addKey("rating");
		writeRating(json, rating);

		json.addKey("votesWildcard");
		json.addNumber(votesWildcard);

		json.addKey("whiteListed");
		json.addBool(whiteListed);

		json.addKey("archived");
		json.addBool(archived);

		json.addKey("dateAdded");
		json.addNumber(dateAdded);

		json.addKey("lastUpdate");
		json.addNumber(lastUpdate);
	}

}

///  Info about how often a number was searched.
class SearchInfo extends _JsonObject {
	///  The phone number
	String phone;

	///  The number of search requests in the {@link #revision time slot}.
	int count;

	///  Some other number of serch requests (context dependent).
	int total;

	///  When the last search request was performed for the {@link #phone number} in the {@link #revision time slot}.
	int lastSearch;

	/// Creates a SearchInfo.
	SearchInfo({
			this.phone = "", 
			this.count = 0, 
			this.total = 0, 
			this.lastSearch = 0, 
	});

	/// Parses a SearchInfo from a string source.
	static SearchInfo? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a SearchInfo instance from the given reader.
	static SearchInfo read(JsonReader json) {
		SearchInfo result = SearchInfo();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "SearchInfo";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "phone": {
				phone = json.expectString();
				break;
			}
			case "count": {
				count = json.expectInt();
				break;
			}
			case "total": {
				total = json.expectInt();
				break;
			}
			case "lastSearch": {
				lastSearch = json.expectInt();
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

		json.addKey("count");
		json.addNumber(count);

		json.addKey("total");
		json.addNumber(total);

		json.addKey("lastSearch");
		json.addNumber(lastSearch);
	}

}

///  Info of how often a certain number was rated in a certain way.
class RatingInfo extends _JsonObject {
	///  The number being rated.
	String phone;

	///  The {@link Rating} of the {@link #phone number}.
	Rating rating;

	///  How often the {@link #phone number} was rated in a {@link #rating certain way}.
	int votes;

	/// Creates a RatingInfo.
	RatingInfo({
			this.phone = "", 
			this.rating = Rating.aLegitimate, 
			this.votes = 0, 
	});

	/// Parses a RatingInfo from a string source.
	static RatingInfo? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a RatingInfo instance from the given reader.
	static RatingInfo read(JsonReader json) {
		RatingInfo result = RatingInfo();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "RatingInfo";

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
	}

}

///  List of blocked numbers for retrieval through the <i>PhoneBlock API</i>.
class Blocklist extends _JsonObject {
	///  Numbers in the blocklist.
	List<BlockListEntry> numbers;

	/// Creates a Blocklist.
	Blocklist({
			this.numbers = const [], 
	});

	/// Parses a Blocklist from a string source.
	static Blocklist? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a Blocklist instance from the given reader.
	static Blocklist read(JsonReader json) {
		Blocklist result = Blocklist();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "Blocklist";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "numbers": {
				json.expectArray();
				numbers = [];
				while (json.hasNext()) {
					if (!json.tryNull()) {
						var value = BlockListEntry.read(json);
						if (value != null) {
							numbers.add(value);
						}
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

		json.addKey("numbers");
		json.startArray();
		for (var _element in numbers) {
			_element.writeContent(json);
		}
		json.endArray();
	}

}

/// Visitor interface for AbstractNumberInfo.
abstract class AbstractNumberInfoVisitor<R, A> {
	R visitNumberInfo(NumberInfo self, A arg);
	R visitNumberHistory(NumberHistory self, A arg);
}

abstract class AbstractNumberInfo extends _JsonObject {
	///  The number being requested.
	String phone;

	///  Whether the number is considered active. Only active numbers are inserted into a blocklist.
	bool active;

	int calls;

	///  The number of votes that support blocking the requested number.
	int votes;

	///  The number ratings of kind "legitimate".
	int ratingLegitimate;

	///  The number ratings of kind "ping".
	int ratingPing;

	///  The number ratings of kind "poll".
	int ratingPoll;

	///  The number ratings of kind "advertising".
	int ratingAdvertising;

	///  The number ratings of kind "gamble".
	int ratingGamble;

	///  The number ratings of kind "fraud".
	int ratingFraud;

	///  The number of search request for this number.
	int searches;

	/// Creates a AbstractNumberInfo.
	AbstractNumberInfo({
			this.phone = "", 
			this.active = false, 
			this.calls = 0, 
			this.votes = 0, 
			this.ratingLegitimate = 0, 
			this.ratingPing = 0, 
			this.ratingPoll = 0, 
			this.ratingAdvertising = 0, 
			this.ratingGamble = 0, 
			this.ratingFraud = 0, 
			this.searches = 0, 
	});

	/// Parses a AbstractNumberInfo from a string source.
	static AbstractNumberInfo? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a AbstractNumberInfo instance from the given reader.
	static AbstractNumberInfo? read(JsonReader json) {
		AbstractNumberInfo? result;

		json.expectArray();
		if (!json.hasNext()) {
			return null;
		}

		switch (json.expectString()) {
			case "NumberInfo": result = NumberInfo(); break;
			case "NumberHistory": result = NumberHistory(); break;
			default: result = null;
		}

		if (!json.hasNext() || json.tryNull()) {
			return null;
		}

		if (result == null) {
			json.skipAnyValue();
		} else {
			result._readContent(json);
		}
		json.endArray();

		return result;
	}

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "phone": {
				phone = json.expectString();
				break;
			}
			case "active": {
				active = json.expectBool();
				break;
			}
			case "calls": {
				calls = json.expectInt();
				break;
			}
			case "votes": {
				votes = json.expectInt();
				break;
			}
			case "ratingLegitimate": {
				ratingLegitimate = json.expectInt();
				break;
			}
			case "ratingPing": {
				ratingPing = json.expectInt();
				break;
			}
			case "ratingPoll": {
				ratingPoll = json.expectInt();
				break;
			}
			case "ratingAdvertising": {
				ratingAdvertising = json.expectInt();
				break;
			}
			case "ratingGamble": {
				ratingGamble = json.expectInt();
				break;
			}
			case "ratingFraud": {
				ratingFraud = json.expectInt();
				break;
			}
			case "searches": {
				searches = json.expectInt();
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

		json.addKey("active");
		json.addBool(active);

		json.addKey("calls");
		json.addNumber(calls);

		json.addKey("votes");
		json.addNumber(votes);

		json.addKey("ratingLegitimate");
		json.addNumber(ratingLegitimate);

		json.addKey("ratingPing");
		json.addNumber(ratingPing);

		json.addKey("ratingPoll");
		json.addNumber(ratingPoll);

		json.addKey("ratingAdvertising");
		json.addNumber(ratingAdvertising);

		json.addKey("ratingGamble");
		json.addNumber(ratingGamble);

		json.addKey("ratingFraud");
		json.addNumber(ratingFraud);

		json.addKey("searches");
		json.addNumber(searches);
	}

	R visitAbstractNumberInfo<R, A>(AbstractNumberInfoVisitor<R, A> v, A arg);

}

///  Represents a row in the PhoneBlock database for a phone number
class NumberInfo extends AbstractNumberInfo {
	///  Time when the number was inserted
	int added;

	///  Time when the information was last updated.
	int updated;

	///  Time when the number was last searched on the web site or through the API.
	int lastSearch;

	/// Creates a NumberInfo.
	NumberInfo({
			super.phone, 
			super.active, 
			super.calls, 
			super.votes, 
			super.ratingLegitimate, 
			super.ratingPing, 
			super.ratingPoll, 
			super.ratingAdvertising, 
			super.ratingGamble, 
			super.ratingFraud, 
			super.searches, 
			this.added = 0, 
			this.updated = 0, 
			this.lastSearch = 0, 
	});

	/// Parses a NumberInfo from a string source.
	static NumberInfo? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a NumberInfo instance from the given reader.
	static NumberInfo read(JsonReader json) {
		NumberInfo result = NumberInfo();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "NumberInfo";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "added": {
				added = json.expectInt();
				break;
			}
			case "updated": {
				updated = json.expectInt();
				break;
			}
			case "lastSearch": {
				lastSearch = json.expectInt();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("added");
		json.addNumber(added);

		json.addKey("updated");
		json.addNumber(updated);

		json.addKey("lastSearch");
		json.addNumber(lastSearch);
	}

	@override
	R visitAbstractNumberInfo<R, A>(AbstractNumberInfoVisitor<R, A> v, A arg) => v.visitNumberInfo(this, arg);

}

///  Represents a row in the PhoneBlock database for an history entry for a phone number
class NumberHistory extends AbstractNumberInfo {
	///  The revision in which this information was stored.
	int rMin;

	///  The revision up to which this information is valid (inclusive).
	int rMax;

	/// Creates a NumberHistory.
	NumberHistory({
			super.phone, 
			super.active, 
			super.calls, 
			super.votes, 
			super.ratingLegitimate, 
			super.ratingPing, 
			super.ratingPoll, 
			super.ratingAdvertising, 
			super.ratingGamble, 
			super.ratingFraud, 
			super.searches, 
			this.rMin = 0, 
			this.rMax = 0, 
	});

	/// Parses a NumberHistory from a string source.
	static NumberHistory? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a NumberHistory instance from the given reader.
	static NumberHistory read(JsonReader json) {
		NumberHistory result = NumberHistory();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "NumberHistory";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "rMin": {
				rMin = json.expectInt();
				break;
			}
			case "rMax": {
				rMax = json.expectInt();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("rMin");
		json.addNumber(rMin);

		json.addKey("rMax");
		json.addNumber(rMax);
	}

	@override
	R visitAbstractNumberInfo<R, A>(AbstractNumberInfoVisitor<R, A> v, A arg) => v.visitNumberHistory(this, arg);

}

class BlockListEntry extends _JsonObject {
	///  The number being requested.
	String phone;

	///  The number of votes that support blocking the requested number.
	int votes;

	///  The rating for the requested phone number.
	Rating rating;

	/// Creates a BlockListEntry.
	BlockListEntry({
			this.phone = "", 
			this.votes = 0, 
			this.rating = Rating.aLegitimate, 
	});

	/// Parses a BlockListEntry from a string source.
	static BlockListEntry? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a BlockListEntry instance from the given reader.
	static BlockListEntry read(JsonReader json) {
		BlockListEntry result = BlockListEntry();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "phone-info";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "phone": {
				phone = json.expectString();
				break;
			}
			case "votes": {
				votes = json.expectInt();
				break;
			}
			case "rating": {
				rating = readRating(json);
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

		json.addKey("votes");
		json.addNumber(votes);

		json.addKey("rating");
		writeRating(json, rating);
	}

}

class Ratings extends _JsonObject {
	List<Rating> values;

	/// Creates a Ratings.
	Ratings({
			this.values = const [], 
	});

	/// Parses a Ratings from a string source.
	static Ratings? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a Ratings instance from the given reader.
	static Ratings read(JsonReader json) {
		Ratings result = Ratings();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "Ratings";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "values": {
				json.expectArray();
				values = [];
				while (json.hasNext()) {
					values.add(readRating(json));
				}
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("values");
		json.startArray();
		for (var _element in values) {
			writeRating(json, _element);
		}
		json.endArray();
	}

}

///  A comment posted for a phone number
class UserComment extends _JsonObject {
	///  Technical identifier of this comment.
	String id;

	///  The user ID of the user that created this comment, or <code>null</code>, if the comment was created by an anonymous user.
	int? userId;

	///  The language tag describing the language of this comment.
	String lang;

	///  The phone number this comment belongs to.
	String phone;

	///  The rating of the comment (1 for positive, 5 for negative).
	Rating rating;

	///  The comment text
	String comment;

	///  The source of the comment, <code>phoneblock</code> for comments entered on the web site.
	String service;

	///  The creation date of the comment in milliseconds since epoch.
	int created;

	///  Number of "thumbs up" ratings for this comment.
	int up;

	///  Number of "thumbs down" ratings for this comment.
	int down;

	/// Creates a UserComment.
	UserComment({
			this.id = "", 
			this.userId, 
			this.lang = "", 
			this.phone = "", 
			this.rating = Rating.aLegitimate, 
			this.comment = "", 
			this.service = "", 
			this.created = 0, 
			this.up = 0, 
			this.down = 0, 
	});

	/// Parses a UserComment from a string source.
	static UserComment? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a UserComment instance from the given reader.
	static UserComment read(JsonReader json) {
		UserComment result = UserComment();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "UserComment";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "id": {
				id = json.expectString();
				break;
			}
			case "userId": {
				userId = json.expectInt();
				break;
			}
			case "lang": {
				lang = json.expectString();
				break;
			}
			case "phone": {
				phone = json.expectString();
				break;
			}
			case "rating": {
				rating = readRating(json);
				break;
			}
			case "comment": {
				comment = json.expectString();
				break;
			}
			case "service": {
				service = json.expectString();
				break;
			}
			case "created": {
				created = json.expectInt();
				break;
			}
			case "up": {
				up = json.expectInt();
				break;
			}
			case "down": {
				down = json.expectInt();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("id");
		json.addString(id);

		var _userId = userId;
		if (_userId != null) {
			json.addKey("userId");
			json.addNumber(_userId);
		}

		json.addKey("lang");
		json.addString(lang);

		json.addKey("phone");
		json.addString(phone);

		json.addKey("rating");
		writeRating(json, rating);

		json.addKey("comment");
		json.addString(comment);

		json.addKey("service");
		json.addString(service);

		json.addKey("created");
		json.addNumber(created);

		json.addKey("up");
		json.addNumber(up);

		json.addKey("down");
		json.addNumber(down);
	}

}

class SpamReport extends _JsonObject {
	String phone;

	int votes;

	int lastUpdate;

	int dateAdded;

	bool archived;

	bool whiteListed;

	///  The number of phone numbers with the the same prefix but a different end digit that are also reported as SPAM.
	int cnt10;

	///  The total number of votes against all phone numbers with the the same prefix but a different end digit.
	int votes10;

	///  The number of phone numbers with the the same prefix but two different end digits that are also reported as SPAM.
	/// 
	///  <p>
	///  This number only considers {@link #cnt10 blocks of phone numbers} with a minimum fill-ratio.
	///  </p>
	int cnt100;

	///  The total number of votes against all phone numbers with the the same prefix but two different end digits. 
	/// 
	///  <p>
	///  This number only considers {@link #cnt10 blocks of phone numbers} with a minimum fill-ratio.
	///  </p>
	int votes100;

	/// Creates a SpamReport.
	SpamReport({
			this.phone = "", 
			this.votes = 0, 
			this.lastUpdate = 0, 
			this.dateAdded = 0, 
			this.archived = false, 
			this.whiteListed = false, 
			this.cnt10 = 0, 
			this.votes10 = 0, 
			this.cnt100 = 0, 
			this.votes100 = 0, 
	});

	/// Parses a SpamReport from a string source.
	static SpamReport? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a SpamReport instance from the given reader.
	static SpamReport read(JsonReader json) {
		SpamReport result = SpamReport();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "SpamReport";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "phone": {
				phone = json.expectString();
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
			case "dateAdded": {
				dateAdded = json.expectInt();
				break;
			}
			case "archived": {
				archived = json.expectBool();
				break;
			}
			case "whiteListed": {
				whiteListed = json.expectBool();
				break;
			}
			case "cnt10": {
				cnt10 = json.expectInt();
				break;
			}
			case "votes10": {
				votes10 = json.expectInt();
				break;
			}
			case "cnt100": {
				cnt100 = json.expectInt();
				break;
			}
			case "votes100": {
				votes100 = json.expectInt();
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

		json.addKey("votes");
		json.addNumber(votes);

		json.addKey("lastUpdate");
		json.addNumber(lastUpdate);

		json.addKey("dateAdded");
		json.addNumber(dateAdded);

		json.addKey("archived");
		json.addBool(archived);

		json.addKey("whiteListed");
		json.addBool(whiteListed);

		json.addKey("cnt10");
		json.addNumber(cnt10);

		json.addKey("votes10");
		json.addNumber(votes10);

		json.addKey("cnt100");
		json.addNumber(cnt100);

		json.addKey("votes100");
		json.addNumber(votes100);
	}

}

class PhoneNumer extends _JsonObject {
	///  The representation stored in the database.
	String id;

	///  The local number with country ID prefx.
	String shortcut;

	String plus;

	String zeroZero;

	String countryCode;

	String country;

	String? cityCode;

	String? city;

	///  The kind of number
	String? usage;

	/// Creates a PhoneNumer.
	PhoneNumer({
			this.id = "", 
			this.shortcut = "", 
			this.plus = "", 
			this.zeroZero = "", 
			this.countryCode = "", 
			this.country = "", 
			this.cityCode, 
			this.city, 
			this.usage, 
	});

	/// Parses a PhoneNumer from a string source.
	static PhoneNumer? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a PhoneNumer instance from the given reader.
	static PhoneNumer read(JsonReader json) {
		PhoneNumer result = PhoneNumer();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "PhoneNumer";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "id": {
				id = json.expectString();
				break;
			}
			case "shortcut": {
				shortcut = json.expectString();
				break;
			}
			case "plus": {
				plus = json.expectString();
				break;
			}
			case "zeroZero": {
				zeroZero = json.expectString();
				break;
			}
			case "countryCode": {
				countryCode = json.expectString();
				break;
			}
			case "country": {
				country = json.expectString();
				break;
			}
			case "cityCode": {
				cityCode = json.expectString();
				break;
			}
			case "city": {
				city = json.expectString();
				break;
			}
			case "usage": {
				usage = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("id");
		json.addString(id);

		json.addKey("shortcut");
		json.addString(shortcut);

		json.addKey("plus");
		json.addString(plus);

		json.addKey("zeroZero");
		json.addString(zeroZero);

		json.addKey("countryCode");
		json.addString(countryCode);

		json.addKey("country");
		json.addString(country);

		var _cityCode = cityCode;
		if (_cityCode != null) {
			json.addKey("cityCode");
			json.addString(_cityCode);
		}

		var _city = city;
		if (_city != null) {
			json.addKey("city");
			json.addString(_city);
		}

		var _usage = usage;
		if (_usage != null) {
			json.addKey("usage");
			json.addString(_usage);
		}
	}

}

/// Visitor interface for AccountData.
abstract class AccountDataVisitor<R, A> {
	R visitUpdateAccountRequest(UpdateAccountRequest self, A arg);
	R visitAccountSettings(AccountSettings self, A arg);
}

///  Base message with common account settings fields.
abstract class AccountData extends _JsonObject {
	///  The preferred language tag (e.g., "de", "en-US", "pt-BR").
	String? lang;

	///  The user's country dial prefix (e.g., "+49", "+1", "+351").
	String? dialPrefix;

	///  The user's display name.
	String? displayName;

	/// Creates a AccountData.
	AccountData({
			this.lang, 
			this.dialPrefix, 
			this.displayName, 
	});

	/// Parses a AccountData from a string source.
	static AccountData? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a AccountData instance from the given reader.
	static AccountData? read(JsonReader json) {
		AccountData? result;

		json.expectArray();
		if (!json.hasNext()) {
			return null;
		}

		switch (json.expectString()) {
			case "UpdateAccountRequest": result = UpdateAccountRequest(); break;
			case "AccountSettings": result = AccountSettings(); break;
			default: result = null;
		}

		if (!json.hasNext() || json.tryNull()) {
			return null;
		}

		if (result == null) {
			json.skipAnyValue();
		} else {
			result._readContent(json);
		}
		json.endArray();

		return result;
	}

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "lang": {
				lang = json.expectString();
				break;
			}
			case "dialPrefix": {
				dialPrefix = json.expectString();
				break;
			}
			case "displayName": {
				displayName = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		var _lang = lang;
		if (_lang != null) {
			json.addKey("lang");
			json.addString(_lang);
		}

		var _dialPrefix = dialPrefix;
		if (_dialPrefix != null) {
			json.addKey("dialPrefix");
			json.addString(_dialPrefix);
		}

		var _displayName = displayName;
		if (_displayName != null) {
			json.addKey("displayName");
			json.addString(_displayName);
		}
	}

	R visitAccountData<R, A>(AccountDataVisitor<R, A> v, A arg);

}

///  Request to update user account settings.
class UpdateAccountRequest extends AccountData {
	///  ISO 3166-1 alpha-2 country code (e.g., "DE", "US", "BR"). If provided, the server will convert it to the corresponding dial prefix.
	String? countryCode;

	/// Creates a UpdateAccountRequest.
	UpdateAccountRequest({
			super.lang, 
			super.dialPrefix, 
			super.displayName, 
			this.countryCode, 
	});

	/// Parses a UpdateAccountRequest from a string source.
	static UpdateAccountRequest? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a UpdateAccountRequest instance from the given reader.
	static UpdateAccountRequest read(JsonReader json) {
		UpdateAccountRequest result = UpdateAccountRequest();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "UpdateAccountRequest";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "countryCode": {
				countryCode = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		var _countryCode = countryCode;
		if (_countryCode != null) {
			json.addKey("countryCode");
			json.addString(_countryCode);
		}
	}

	@override
	R visitAccountData<R, A>(AccountDataVisitor<R, A> v, A arg) => v.visitUpdateAccountRequest(this, arg);

}

///  Response from account settings operations.
class AccountSettings extends AccountData {
	///  The user's email address.
	String? email;

	/// Creates a AccountSettings.
	AccountSettings({
			super.lang, 
			super.dialPrefix, 
			super.displayName, 
			this.email, 
	});

	/// Parses a AccountSettings from a string source.
	static AccountSettings? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a AccountSettings instance from the given reader.
	static AccountSettings read(JsonReader json) {
		AccountSettings result = AccountSettings();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "AccountSettings";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "email": {
				email = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		var _email = email;
		if (_email != null) {
			json.addKey("email");
			json.addString(_email);
		}
	}

	@override
	R visitAccountData<R, A>(AccountDataVisitor<R, A> v, A arg) => v.visitAccountSettings(this, arg);

}

///  Entry in a personalized number list with optional comment.
class PersonalizedNumber extends _JsonObject {
	/// The phone number in international format (for API communication).
	String phone;

	/// The phone number formatted according to user's locale/country (for display).
	String? label;

	/// User's comment for this number (may be null).
	String? comment;

	/// Creates a PersonalizedNumber.
	PersonalizedNumber({
			this.phone = "",
			this.label,
			this.comment,
	});

	/// Parses a PersonalizedNumber from a string source.
	static PersonalizedNumber? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a PersonalizedNumber instance from the given reader.
	static PersonalizedNumber read(JsonReader json) {
		PersonalizedNumber result = PersonalizedNumber();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "PersonalizedNumber";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "phone": {
				phone = json.expectString();
				break;
			}
			case "label": {
				label = json.expectString();
				break;
			}
			case "comment": {
				comment = json.expectString();
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

		var _label = label;
		if (_label != null) {
			json.addKey("label");
			json.addString(_label);
		}

		var _comment = comment;
		if (_comment != null) {
			json.addKey("comment");
			json.addString(_comment);
		}
	}

}

///  A list of phone numbers (used for blacklist/whitelist responses).
class NumberList extends _JsonObject {
	///  Phone numbers with optional comments.
	List<PersonalizedNumber> numbers;

	/// Creates a NumberList.
	NumberList({
			this.numbers = const [], 
	});

	/// Parses a NumberList from a string source.
	static NumberList? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a NumberList instance from the given reader.
	static NumberList read(JsonReader json) {
		NumberList result = NumberList();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "NumberList";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "numbers": {
				json.expectArray();
				numbers = [];
				while (json.hasNext()) {
					if (!json.tryNull()) {
						var value = PersonalizedNumber.read(json);
						if (value != null) {
							numbers.add(value);
						}
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

		json.addKey("numbers");
		json.startArray();
		for (var _element in numbers) {
			_element.writeContent(json);
		}
		json.endArray();
	}

}

