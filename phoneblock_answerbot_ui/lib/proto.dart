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

