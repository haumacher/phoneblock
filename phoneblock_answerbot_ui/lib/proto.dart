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

class CreateAnswerbotResponse extends _JsonObject {
	String userName;

	/// Creates a CreateAnswerbotResponse.
	CreateAnswerbotResponse({
			this.userName = "", 
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
			case "userName": {
				userName = json.expectString();
				break;
			}
			default: super._readProperty(key, json);
		}
	}

	@override
	void _writeProperties(JsonSink json) {
		super._writeProperties(json);

		json.addKey("userName");
		json.addString(userName);
	}

}

///  Information of a single answer bot.
class AnswerbotInfo extends _JsonObject {
	///  The primary key identifier of this bot.
	int id;

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

