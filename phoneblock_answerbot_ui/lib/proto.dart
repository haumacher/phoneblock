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
abstract class SetupRequestVisitor<R, A> {
	R visitCreateAnswerBot(CreateAnswerBot self, A arg);
	R visitEnterHostName(EnterHostName self, A arg);
	R visitSetupDynDns(SetupDynDns self, A arg);
	R visitCheckDynDns(CheckDynDns self, A arg);
	R visitEnableAnswerBot(EnableAnswerBot self, A arg);
	R visitDisableAnswerBot(DisableAnswerBot self, A arg);
	R visitCheckAnswerBot(CheckAnswerBot self, A arg);
}

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
			case "EnableAnswerBot": result = EnableAnswerBot(); break;
			case "DisableAnswerBot": result = DisableAnswerBot(); break;
			case "CheckAnswerBot": result = CheckAnswerBot(); break;
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

class EnterHostName extends SetupRequest {
	int id;

	String hostName;

	/// Creates a EnterHostName.
	EnterHostName({
			this.id = 0, 
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
			case "id": {
				id = json.expectInt();
				break;
			}
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

		json.addKey("id");
		json.addNumber(id);

		json.addKey("hostName");
		json.addString(hostName);
	}

	@override
	R visitSetupRequest<R, A>(SetupRequestVisitor<R, A> v, A arg) => v.visitEnterHostName(this, arg);

}

class SetupDynDns extends SetupRequest {
	int id;

	String hostName;

	/// Creates a SetupDynDns.
	SetupDynDns({
			this.id = 0, 
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
			case "id": {
				id = json.expectInt();
				break;
			}
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

		json.addKey("id");
		json.addNumber(id);

		json.addKey("hostName");
		json.addString(hostName);
	}

	@override
	R visitSetupRequest<R, A>(SetupRequestVisitor<R, A> v, A arg) => v.visitSetupDynDns(this, arg);

}

class SetupDynDnsResponse extends _JsonObject {
	int id;

	String dyndnsUser;

	String dyndnsPassword;

	/// Creates a SetupDynDnsResponse.
	SetupDynDnsResponse({
			this.id = 0, 
			this.dyndnsUser = "", 
			this.dyndnsPassword = "", 
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
	}

}

class CheckDynDns extends SetupRequest {
	int id;

	/// Creates a CheckDynDns.
	CheckDynDns({
			this.id = 0, 
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

	@override
	R visitSetupRequest<R, A>(SetupRequestVisitor<R, A> v, A arg) => v.visitCheckDynDns(this, arg);

}

class EnableAnswerBot extends SetupRequest {
	int id;

	/// Creates a EnableAnswerBot.
	EnableAnswerBot({
			this.id = 0, 
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

	@override
	R visitSetupRequest<R, A>(SetupRequestVisitor<R, A> v, A arg) => v.visitEnableAnswerBot(this, arg);

}

class DisableAnswerBot extends SetupRequest {
	int id;

	/// Creates a DisableAnswerBot.
	DisableAnswerBot({
			this.id = 0, 
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

	@override
	R visitSetupRequest<R, A>(SetupRequestVisitor<R, A> v, A arg) => v.visitDisableAnswerBot(this, arg);

}

class CheckAnswerBot extends SetupRequest {
	int id;

	/// Creates a CheckAnswerBot.
	CheckAnswerBot({
			this.id = 0, 
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

	@override
	R visitSetupRequest<R, A>(SetupRequestVisitor<R, A> v, A arg) => v.visitCheckAnswerBot(this, arg);

}

///  Information of a single answer bot.
class AnswerbotInfo extends _JsonObject {
	///  The primary key identifier of this bot.
	int id;

	///  The ID of the owning user.
	int userId;

	///  Whether the bot is enabled (registration is active).
	bool enabled;

	///  Whether the bot has sucessfully registered (can accept calls).
	bool registered;

	///  The message received during the last registration attempt.
	String? registerMsg;

	///  The total number of calls accepted by this bot so far.
	int callsAccepted;

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
				this.registered = false, 
				this.registerMsg, 
				this.callsAccepted = 0, 
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
				case "registered": {
					registered = json.expectBool();
					break;
				}
				case "registerMsg": {
					registerMsg = json.expectString();
					break;
				}
				case "callsAccepted": {
					callsAccepted = json.expectInt();
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

			json.addKey("registered");
			json.addBool(registered);

			var _registerMsg = registerMsg;
			if (_registerMsg != null) {
				json.addKey("registerMsg");
				json.addString(_registerMsg);
			}

			json.addKey("callsAccepted");
			json.addNumber(callsAccepted);

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
						bots.add(AnswerbotInfo.read(json));
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

