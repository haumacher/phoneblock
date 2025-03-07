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

/// Visitor interface for SetupRequest.
abstract class SetupRequestVisitor<R, A> implements BotRequestVisitor<R, A> {
	R visitCreateAnswerBot(CreateAnswerBot self, A arg);
}

///  Base class for all supported requests.
abstract class SetupRequest extends _JsonObject {
	/// Creates a SetupRequest.
	SetupRequest();

	/// Parses a SetupRequest from a string source.
	static SetupRequest? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a SetupRequest instance from the given reader.
	static SetupRequest? read(JsonReader json) {
		SetupRequest? result;

		json.expectArray();
		if (!json.hasNext()) {
			return null;
		}

		switch (json.expectString()) {
			case "CreateAnswerBot": result = CreateAnswerBot(); break;
			case "EnterHostName": result = EnterHostName(); break;
			case "SetupDynDns": result = SetupDynDns(); break;
			case "CheckDynDns": result = CheckDynDns(); break;
			case "UpdateAnswerBot": result = UpdateAnswerBot(); break;
			case "EnableAnswerBot": result = EnableAnswerBot(); break;
			case "DisableAnswerBot": result = DisableAnswerBot(); break;
			case "DeleteAnswerBot": result = DeleteAnswerBot(); break;
			case "CheckAnswerBot": result = CheckAnswerBot(); break;
			case "ListCalls": result = ListCalls(); break;
			case "ClearCallList": result = ClearCallList(); break;
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

	R visitSetupRequest<R, A>(SetupRequestVisitor<R, A> v, A arg);

}

class CreateAnswerbotResponse extends _JsonObject {
	int id;

	String userName;

	String password;

	/// Creates a CreateAnswerbotResponse.
	CreateAnswerbotResponse({
			this.id = 0, 
			this.userName = "", 
			this.password = "", 
	});

	/// Parses a CreateAnswerbotResponse from a string source.
	static CreateAnswerbotResponse? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a CreateAnswerbotResponse instance from the given reader.
	static CreateAnswerbotResponse read(JsonReader json) {
		CreateAnswerbotResponse result = CreateAnswerbotResponse();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "CreateAnswerbotResponse";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "id": {
				id = json.expectInt();
				break;
			}
			case "userName": {
				userName = json.expectString();
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

		json.addKey("id");
		json.addNumber(id);

		json.addKey("userName");
		json.addString(userName);

		json.addKey("password");
		json.addString(password);
	}

}

class CreateAnswerBot extends SetupRequest {
	/// Creates a CreateAnswerBot.
	CreateAnswerBot();

	/// Parses a CreateAnswerBot from a string source.
	static CreateAnswerBot? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a CreateAnswerBot instance from the given reader.
	static CreateAnswerBot read(JsonReader json) {
		CreateAnswerBot result = CreateAnswerBot();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "CreateAnswerBot";

	@override
	R visitSetupRequest<R, A>(SetupRequestVisitor<R, A> v, A arg) => v.visitCreateAnswerBot(this, arg);

}

/// Visitor interface for BotRequest.
abstract class BotRequestVisitor<R, A> {
	R visitEnterHostName(EnterHostName self, A arg);
	R visitSetupDynDns(SetupDynDns self, A arg);
	R visitCheckDynDns(CheckDynDns self, A arg);
	R visitUpdateAnswerBot(UpdateAnswerBot self, A arg);
	R visitEnableAnswerBot(EnableAnswerBot self, A arg);
	R visitDisableAnswerBot(DisableAnswerBot self, A arg);
	R visitDeleteAnswerBot(DeleteAnswerBot self, A arg);
	R visitCheckAnswerBot(CheckAnswerBot self, A arg);
	R visitListCalls(ListCalls self, A arg);
	R visitClearCallList(ClearCallList self, A arg);
}

///  Base class for all requests targeting a single answer bot.
abstract class BotRequest extends SetupRequest {
	///  The ID of the answer bot this request is targeted to
	int id;

	/// Creates a BotRequest.
	BotRequest({
			this.id = 0, 
	});

	/// Parses a BotRequest from a string source.
	static BotRequest? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a BotRequest instance from the given reader.
	static BotRequest? read(JsonReader json) {
		BotRequest? result;

		json.expectArray();
		if (!json.hasNext()) {
			return null;
		}

		switch (json.expectString()) {
			case "EnterHostName": result = EnterHostName(); break;
			case "SetupDynDns": result = SetupDynDns(); break;
			case "CheckDynDns": result = CheckDynDns(); break;
			case "UpdateAnswerBot": result = UpdateAnswerBot(); break;
			case "EnableAnswerBot": result = EnableAnswerBot(); break;
			case "DisableAnswerBot": result = DisableAnswerBot(); break;
			case "DeleteAnswerBot": result = DeleteAnswerBot(); break;
			case "CheckAnswerBot": result = CheckAnswerBot(); break;
			case "ListCalls": result = ListCalls(); break;
			case "ClearCallList": result = ClearCallList(); break;
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
			case "id": {
				id = json.expectInt();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("id");
		json.addNumber(id);
	}

	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg);

	@override
	R visitSetupRequest<R, A>(SetupRequestVisitor<R, A> v, A arg) => visitBotRequest(v, arg);

}

///  Sets the host name of the Fritz!Box to connect the answer bot to.
class EnterHostName extends BotRequest {
	String hostName;

	/// Creates a EnterHostName.
	EnterHostName({
			super.id, 
			this.hostName = "", 
	});

	/// Parses a EnterHostName from a string source.
	static EnterHostName? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a EnterHostName instance from the given reader.
	static EnterHostName read(JsonReader json) {
		EnterHostName result = EnterHostName();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "EnterHostName";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "hostName": {
				hostName = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("hostName");
		json.addString(hostName);
	}

	@override
	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg) => v.visitEnterHostName(this, arg);

}

///  Sets up a DnyDNS account for the Fritz!Box to register its IP address.
class SetupDynDns extends BotRequest {
	String hostName;

	/// Creates a SetupDynDns.
	SetupDynDns({
			super.id, 
			this.hostName = "", 
	});

	/// Parses a SetupDynDns from a string source.
	static SetupDynDns? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a SetupDynDns instance from the given reader.
	static SetupDynDns read(JsonReader json) {
		SetupDynDns result = SetupDynDns();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "SetupDynDns";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "hostName": {
				hostName = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("hostName");
		json.addString(hostName);
	}

	@override
	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg) => v.visitSetupDynDns(this, arg);

}

class SetupDynDnsResponse extends _JsonObject {
	int id;

	String dyndnsUser;

	String dyndnsPassword;

	String dyndnsDomain;

	/// Creates a SetupDynDnsResponse.
	SetupDynDnsResponse({
			this.id = 0, 
			this.dyndnsUser = "", 
			this.dyndnsPassword = "", 
			this.dyndnsDomain = "", 
	});

	/// Parses a SetupDynDnsResponse from a string source.
	static SetupDynDnsResponse? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a SetupDynDnsResponse instance from the given reader.
	static SetupDynDnsResponse read(JsonReader json) {
		SetupDynDnsResponse result = SetupDynDnsResponse();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "SetupDynDnsResponse";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "id": {
				id = json.expectInt();
				break;
			}
			case "dyndnsUser": {
				dyndnsUser = json.expectString();
				break;
			}
			case "dyndnsPassword": {
				dyndnsPassword = json.expectString();
				break;
			}
			case "dyndnsDomain": {
				dyndnsDomain = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("id");
		json.addNumber(id);

		json.addKey("dyndnsUser");
		json.addString(dyndnsUser);

		json.addKey("dyndnsPassword");
		json.addString(dyndnsPassword);

		json.addKey("dyndnsDomain");
		json.addString(dyndnsDomain);
	}

}

///  Checks, whether a DynDNS request has been received.
class CheckDynDns extends BotRequest {
	/// Creates a CheckDynDns.
	CheckDynDns({
			super.id, 
	});

	/// Parses a CheckDynDns from a string source.
	static CheckDynDns? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a CheckDynDns instance from the given reader.
	static CheckDynDns read(JsonReader json) {
		CheckDynDns result = CheckDynDns();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "CheckDynDns";

	@override
	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg) => v.visitCheckDynDns(this, arg);

}

///  Switches the answer bot on.
class UpdateAnswerBot extends BotRequest {
	///  Whether the bot is enabled (registration is active).
	bool enabled;

	///  Whether to limit communication to IPv4.
	bool preferIPv4;

	///  The minimum PhoneBlock votes to consider a call as SPAM and accept it.
	int minVotes;

	///  Whether to block whole number ranges, when a great density of nearby SPAM numbers is detected.
	bool wildcards;

	/// Creates a UpdateAnswerBot.
	UpdateAnswerBot({
			super.id, 
			this.enabled = false, 
			this.preferIPv4 = false, 
			this.minVotes = 0, 
			this.wildcards = false, 
	});

	/// Parses a UpdateAnswerBot from a string source.
	static UpdateAnswerBot? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a UpdateAnswerBot instance from the given reader.
	static UpdateAnswerBot read(JsonReader json) {
		UpdateAnswerBot result = UpdateAnswerBot();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "UpdateAnswerBot";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "enabled": {
				enabled = json.expectBool();
				break;
			}
			case "preferIPv4": {
				preferIPv4 = json.expectBool();
				break;
			}
			case "minVotes": {
				minVotes = json.expectInt();
				break;
			}
			case "wildcards": {
				wildcards = json.expectBool();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("enabled");
		json.addBool(enabled);

		json.addKey("preferIPv4");
		json.addBool(preferIPv4);

		json.addKey("minVotes");
		json.addNumber(minVotes);

		json.addKey("wildcards");
		json.addBool(wildcards);
	}

	@override
	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg) => v.visitUpdateAnswerBot(this, arg);

}

///  Switches the answer bot on.
class EnableAnswerBot extends BotRequest {
	/// Creates a EnableAnswerBot.
	EnableAnswerBot({
			super.id, 
	});

	/// Parses a EnableAnswerBot from a string source.
	static EnableAnswerBot? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a EnableAnswerBot instance from the given reader.
	static EnableAnswerBot read(JsonReader json) {
		EnableAnswerBot result = EnableAnswerBot();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "EnableAnswerBot";

	@override
	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg) => v.visitEnableAnswerBot(this, arg);

}

///  Switches the answer bot off.
class DisableAnswerBot extends BotRequest {
	/// Creates a DisableAnswerBot.
	DisableAnswerBot({
			super.id, 
	});

	/// Parses a DisableAnswerBot from a string source.
	static DisableAnswerBot? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a DisableAnswerBot instance from the given reader.
	static DisableAnswerBot read(JsonReader json) {
		DisableAnswerBot result = DisableAnswerBot();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "DisableAnswerBot";

	@override
	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg) => v.visitDisableAnswerBot(this, arg);

}

///  Deletes an answer bot.
class DeleteAnswerBot extends BotRequest {
	/// Creates a DeleteAnswerBot.
	DeleteAnswerBot({
			super.id, 
	});

	/// Parses a DeleteAnswerBot from a string source.
	static DeleteAnswerBot? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a DeleteAnswerBot instance from the given reader.
	static DeleteAnswerBot read(JsonReader json) {
		DeleteAnswerBot result = DeleteAnswerBot();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "DeleteAnswerBot";

	@override
	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg) => v.visitDeleteAnswerBot(this, arg);

}

///  Checks whether an answer bot has successfully registered to its Fritz!Box.
class CheckAnswerBot extends BotRequest {
	/// Creates a CheckAnswerBot.
	CheckAnswerBot({
			super.id, 
	});

	/// Parses a CheckAnswerBot from a string source.
	static CheckAnswerBot? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a CheckAnswerBot instance from the given reader.
	static CheckAnswerBot read(JsonReader json) {
		CheckAnswerBot result = CheckAnswerBot();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "CheckAnswerBot";

	@override
	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg) => v.visitCheckAnswerBot(this, arg);

}

///  Retrieves a list of calls this answer bot has already answered.
class ListCalls extends BotRequest {
	/// Creates a ListCalls.
	ListCalls({
			super.id, 
	});

	/// Parses a ListCalls from a string source.
	static ListCalls? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a ListCalls instance from the given reader.
	static ListCalls read(JsonReader json) {
		ListCalls result = ListCalls();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "ListCalls";

	@override
	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg) => v.visitListCalls(this, arg);

}

///  Answer to a {@link ListCalls} request.
class ListCallsResponse extends _JsonObject {
	///  The total number of calls answered so far.
	int callsAnswered;

	///  The total amout of time taked to SPAM callers so far.
	int talkTime;

	///  The last calls that have been answered.
	List<CallInfo> calls;

	/// Creates a ListCallsResponse.
	ListCallsResponse({
			this.callsAnswered = 0, 
			this.talkTime = 0, 
			this.calls = const [], 
	});

	/// Parses a ListCallsResponse from a string source.
	static ListCallsResponse? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a ListCallsResponse instance from the given reader.
	static ListCallsResponse read(JsonReader json) {
		ListCallsResponse result = ListCallsResponse();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "ListCallsResponse";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "callsAnswered": {
				callsAnswered = json.expectInt();
				break;
			}
			case "talkTime": {
				talkTime = json.expectInt();
				break;
			}
			case "calls": {
				json.expectArray();
				calls = [];
				while (json.hasNext()) {
					if (!json.tryNull()) {
						var value = CallInfo.read(json);
						if (value != null) {
							calls.add(value);
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

		json.addKey("callsAnswered");
		json.addNumber(callsAnswered);

		json.addKey("talkTime");
		json.addNumber(talkTime);

		json.addKey("calls");
		json.startArray();
		for (var _element in calls) {
			_element.writeContent(json);
		}
		json.endArray();
	}

}

///  Information about a SPAM call answered.
class CallInfo extends _JsonObject {
	///  The phone number of the caller.
	String caller;

	///  The time the call has started.
	int started;

	///  The duration of the call in milliseconds.
	int duration;

	/// Creates a CallInfo.
	CallInfo({
			this.caller = "", 
			this.started = 0, 
			this.duration = 0, 
	});

	/// Parses a CallInfo from a string source.
	static CallInfo? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a CallInfo instance from the given reader.
	static CallInfo read(JsonReader json) {
		CallInfo result = CallInfo();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "CallInfo";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "caller": {
				caller = json.expectString();
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

		json.addKey("caller");
		json.addString(caller);

		json.addKey("started");
		json.addNumber(started);

		json.addKey("duration");
		json.addNumber(duration);
	}

}

///  Information of a single answer bot.
class AnswerbotInfo extends _JsonObject {
	///  The primary key identifier of this bot.
	int id;

	///  The ID of the owning user.
	int userId;

	///  Whether the bot is enabled (registration is active).
	bool enabled;

	///  Whether to limit communication to IPv4.
	bool preferIPv4;

	///  The minimum PhoneBlock votes to consider a call as SPAM and accept it.
	int minVotes;

	///  Whether to block whole number ranges, when a great density of nearby SPAM numbers is detected.
	bool wildcards;

	///  Whether the bot has sucessfully registered (can accept calls).
	bool registered;

	///  The message received during the last registration attempt.
	String? registerMsg;

	///  Number of new calls (reset when clearing the call list).
	int newCalls;

	///  The total number of calls accepted by this bot so far.
	int callsAccepted;

	///  The total time in milliseconds taked to SPAM customers.
	int talkTime;

	///  The name of the box to register at.
	String registrar;

	///  The authentication realm expected for registration.
	String realm;

	///  The user name used for SIP registration.
	String userName;

	///  The password for SIP registration.
	String password;

	///  The host name of the box to register at (only set, if a third-party DynDNS service is used.
		String? host;

		///  The IPv4 address of the box to register at (only filled, if internal DynDNS is set up and succeeded).
		String? ip4;

		///  The IPv6 address of the box to register at (only filled, if internal DynDNS is set up and succeeded).
		String? ip6;

		///  The user name for DynDNS registration of the box (only filled, if internal DynDNS is set up).
		String? dyndnsUser;

		///  The password for DynDNS registration of the box.
		String? dyndnsPassword;

		/// Creates a AnswerbotInfo.
		AnswerbotInfo({
				this.id = 0, 
				this.userId = 0, 
				this.enabled = false, 
				this.preferIPv4 = false, 
				this.minVotes = 0, 
				this.wildcards = false, 
				this.registered = false, 
				this.registerMsg, 
				this.newCalls = 0, 
				this.callsAccepted = 0, 
				this.talkTime = 0, 
				this.registrar = "", 
				this.realm = "", 
				this.userName = "", 
				this.password = "", 
				this.host, 
				this.ip4, 
				this.ip6, 
				this.dyndnsUser, 
				this.dyndnsPassword, 
		});

		/// Parses a AnswerbotInfo from a string source.
		static AnswerbotInfo? fromString(String source) {
			return read(JsonReader.fromString(source));
		}

		/// Reads a AnswerbotInfo instance from the given reader.
		static AnswerbotInfo read(JsonReader json) {
			AnswerbotInfo result = AnswerbotInfo();
			result._readContent(json);
			return result;
		}

		@override
		String _jsonType() => "AnswerbotInfo";

		@override
		void _readProperty(String key, JsonReader json) {
			switch (key) {
				case "id": {
					id = json.expectInt();
					break;
				}
				case "userId": {
					userId = json.expectInt();
					break;
				}
				case "enabled": {
					enabled = json.expectBool();
					break;
				}
				case "preferIPv4": {
					preferIPv4 = json.expectBool();
					break;
				}
				case "minVotes": {
					minVotes = json.expectInt();
					break;
				}
				case "wildcards": {
					wildcards = json.expectBool();
					break;
				}
				case "registered": {
					registered = json.expectBool();
					break;
				}
				case "registerMsg": {
					registerMsg = json.expectString();
					break;
				}
				case "newCalls": {
					newCalls = json.expectInt();
					break;
				}
				case "callsAccepted": {
					callsAccepted = json.expectInt();
					break;
				}
				case "talkTime": {
					talkTime = json.expectInt();
					break;
				}
				case "registrar": {
					registrar = json.expectString();
					break;
				}
				case "realm": {
					realm = json.expectString();
					break;
				}
				case "userName": {
					userName = json.expectString();
					break;
				}
				case "password": {
					password = json.expectString();
					break;
				}
				case "host": {
					host = json.expectString();
					break;
				}
				case "ip4": {
					ip4 = json.expectString();
					break;
				}
				case "ip6": {
					ip6 = json.expectString();
					break;
				}
				case "dyndnsUser": {
					dyndnsUser = json.expectString();
					break;
				}
				case "dyndnsPassword": {
					dyndnsPassword = json.expectString();
					break;
				}
				default: super._readProperty(key, json);
			}
		}

		@override
		void _writeProperties(JsonSink json) {
			super._writeProperties(json);

			json.addKey("id");
			json.addNumber(id);

			json.addKey("userId");
			json.addNumber(userId);

			json.addKey("enabled");
			json.addBool(enabled);

			json.addKey("preferIPv4");
			json.addBool(preferIPv4);

			json.addKey("minVotes");
			json.addNumber(minVotes);

			json.addKey("wildcards");
			json.addBool(wildcards);

			json.addKey("registered");
			json.addBool(registered);

			var _registerMsg = registerMsg;
			if (_registerMsg != null) {
				json.addKey("registerMsg");
				json.addString(_registerMsg);
			}

			json.addKey("newCalls");
			json.addNumber(newCalls);

			json.addKey("callsAccepted");
			json.addNumber(callsAccepted);

			json.addKey("talkTime");
			json.addNumber(talkTime);

			json.addKey("registrar");
			json.addString(registrar);

			json.addKey("realm");
			json.addString(realm);

			json.addKey("userName");
			json.addString(userName);

			json.addKey("password");
			json.addString(password);

			var _host = host;
			if (_host != null) {
				json.addKey("host");
				json.addString(_host);
			}

			var _ip4 = ip4;
			if (_ip4 != null) {
				json.addKey("ip4");
				json.addString(_ip4);
			}

			var _ip6 = ip6;
			if (_ip6 != null) {
				json.addKey("ip6");
				json.addString(_ip6);
			}

			var _dyndnsUser = dyndnsUser;
			if (_dyndnsUser != null) {
				json.addKey("dyndnsUser");
				json.addString(_dyndnsUser);
			}

			var _dyndnsPassword = dyndnsPassword;
			if (_dyndnsPassword != null) {
				json.addKey("dyndnsPassword");
				json.addString(_dyndnsPassword);
			}
		}

	}

///  Clears the calls answered so far.
class ClearCallList extends BotRequest {
	/// Creates a ClearCallList.
	ClearCallList({
			super.id, 
	});

	/// Parses a ClearCallList from a string source.
	static ClearCallList? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a ClearCallList instance from the given reader.
	static ClearCallList read(JsonReader json) {
		ClearCallList result = ClearCallList();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "ClearCallList";

	@override
	R visitBotRequest<R, A>(BotRequestVisitor<R, A> v, A arg) => v.visitClearCallList(this, arg);

}

///  Result of the {@link de.haumacher.phoneblock.ab.ListABServlet}.
class ListAnswerbotResponse extends _JsonObject {
	///  Infos for all answer bots of the current user.
	List<AnswerbotInfo> bots;

	/// Creates a ListAnswerbotResponse.
	ListAnswerbotResponse({
			this.bots = const [], 
	});

	/// Parses a ListAnswerbotResponse from a string source.
	static ListAnswerbotResponse? fromString(String source) {
		return read(JsonReader.fromString(source));
	}

	/// Reads a ListAnswerbotResponse instance from the given reader.
	static ListAnswerbotResponse read(JsonReader json) {
		ListAnswerbotResponse result = ListAnswerbotResponse();
		result._readContent(json);
		return result;
	}

	@override
	String _jsonType() => "ListAnswerbotResponse";

	@override
	void _readProperty(String key, JsonReader json) {
		switch (key) {
			case "bots": {
				json.expectArray();
				bots = [];
				while (json.hasNext()) {
					if (!json.tryNull()) {
						var value = AnswerbotInfo.read(json);
						if (value != null) {
							bots.add(value);
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

		json.addKey("bots");
		json.startArray();
		for (var _element in bots) {
			_element.writeContent(json);
		}
		json.endArray();
	}

}

