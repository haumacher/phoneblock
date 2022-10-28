package de.haumacher.phoneblock.analysis;

public class AreaCodes extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link AreaCodes} instance.
	 */
	public static AreaCodes create() {
		return new de.haumacher.phoneblock.analysis.AreaCodes();
	}

	/** Identifier for the {@link AreaCodes} type in JSON format. */
	public static final String AREA_CODES__TYPE = "AreaCodes";

	/** @see #getCodes() */
	public static final String CODES__PROP = "codes";

	/** Identifier for the property {@link #getCodes()} in binary format. */
	static final int CODES__ID = 1;

	private final java.util.Map<String, AreaCode> _codes = new java.util.HashMap<>();

	/**
	 * Creates a {@link AreaCodes} instance.
	 *
	 * @see AreaCodes#create()
	 */
	protected AreaCodes() {
		super();
	}

	public final java.util.Map<String, AreaCode> getCodes() {
		return _codes;
	}

	/**
	 * @see #getCodes()
	 */
	public AreaCodes setCodes(java.util.Map<String, AreaCode> value) {
		internalSetCodes(value);
		return this;
	}

	/** Internal setter for {@link #getCodes()} without chain call utility. */
	protected final void internalSetCodes(java.util.Map<String, AreaCode> value) {
		if (value == null) throw new IllegalArgumentException("Property 'codes' cannot be null.");
		_codes.clear();
		_codes.putAll(value);
	}

	/**
	 * Adds a key value pair to the {@link #getCodes()} map.
	 */
	public AreaCodes putCode(String key, AreaCode value) {
		internalPutCode(key, value);
		return this;
	}

	/** Implementation of {@link #putCode(String, AreaCode)} without chain call utility. */
	protected final void  internalPutCode(String key, AreaCode value) {
		if (_codes.containsKey(key)) {
			throw new IllegalArgumentException("Property 'codes' already contains a value for key '" + key + "'.");
		}
		_codes.put(key, value);
	}

	/**
	 * Removes a key from the {@link #getCodes()} map.
	 */
	public final void removeCode(String key) {
		_codes.remove(key);
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public AreaCodes registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public AreaCodes unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return AREA_CODES__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			CODES__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case CODES__PROP: return getCodes();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case CODES__PROP: internalSetCodes((java.util.Map<String, AreaCode>) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static AreaCodes readAreaCodes(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.analysis.AreaCodes result = new de.haumacher.phoneblock.analysis.AreaCodes();
		result.readContent(in);
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		writeContent(out);
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(CODES__PROP);
		out.beginObject();
		for (java.util.Map.Entry<String,AreaCode> entry : getCodes().entrySet()) {
			out.name(entry.getKey());
			entry.getValue().writeTo(out);
		}
		out.endObject();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case CODES__PROP: {
				in.beginObject();
				while (in.hasNext()) {
					putCode(in.nextName(), de.haumacher.phoneblock.analysis.AreaCode.readAreaCode(in));
				}
				in.endObject();
				break;
			}
			default: super.readField(in, field);
		}
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.beginObject();
		writeFields(out);
		out.endObject();
	}

	/**
	 * Serializes all fields of this instance to the given binary output.
	 *
	 * @param out
	 *        The binary output to write to.
	 * @throws java.io.IOException If writing fails.
	 */
	protected void writeFields(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.name(CODES__ID);
	}

	/** Reads a new instance from the given reader. */
	public static AreaCodes readAreaCodes(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		AreaCodes result = de.haumacher.phoneblock.analysis.AreaCodes.readAreaCodes_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link AreaCodes} from a polymorphic composition. */
	public static AreaCodes readAreaCodes_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.analysis.AreaCodes result = new AreaCodes();
		result.readContent(in);
		return result;
	}

	/** Helper for reading all fields of this instance. */
	protected final void readContent(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		while (in.hasNext()) {
			int field = in.nextName();
			readField(in, field);
		}
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case CODES__ID: {
				in.beginArray();
				while (in.hasNext()) {
					in.beginObject();
					String key = "";
					AreaCode value = null;
					while (in.hasNext()) {
						switch (in.nextName()) {
							case 1: key = in.nextString(); break;
							case 2: value = de.haumacher.phoneblock.analysis.AreaCode.readAreaCode(in); break;
							default: in.skipValue(); break;
						}
					}
					putCode(key, value);
					in.endObject();
				}
				in.endArray();
				break;
			}
			default: in.skipValue(); 
		}
	}

}
