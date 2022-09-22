package de.haumacher.phoneblock.index.google;

public class UpdateMessage extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	public enum Type implements de.haumacher.msgbuf.data.ProtocolEnum {

		URL_UPDATED("URL_UPDATED"),

		URL_DELETED("URL_DELETED"),

		;

		private final String _protocolName;

		private Type(String protocolName) {
			_protocolName = protocolName;
		}

		/**
		 * The protocol name of a {@link Type} constant.
		 *
		 * @see #valueOfProtocol(String)
		 */
		@Override
		public String protocolName() {
			return _protocolName;
		}

		/** Looks up a {@link Type} constant by it's protocol name. */
		public static Type valueOfProtocol(String protocolName) {
			if (protocolName == null) { return null; }
			switch (protocolName) {
				case "URL_UPDATED": return URL_UPDATED;
				case "URL_DELETED": return URL_DELETED;
			}
			return URL_UPDATED;
		}

		/** Writes this instance to the given output. */
		public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
			out.value(protocolName());
		}

		/** Reads a new instance from the given reader. */
		public static Type readType(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
			return valueOfProtocol(in.nextString());
		}

		/** Writes this instance to the given binary output. */
		public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
			switch (this) {
				case URL_UPDATED: out.value(1); break;
				case URL_DELETED: out.value(2); break;
				default: out.value(0);
			}
		}

		/** Reads a new instance from the given binary reader. */
		public static Type readType(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
			switch (in.nextInt()) {
				case 1: return URL_UPDATED;
				case 2: return URL_DELETED;
				default: return URL_UPDATED;
			}
		}
	}

	/**
	 * Creates a {@link UpdateMessage} instance.
	 */
	public static UpdateMessage create() {
		return new UpdateMessage();
	}

	/** Identifier for the {@link UpdateMessage} type in JSON format. */
	public static final String UPDATE_MESSAGE__TYPE = "UpdateMessage";

	/** @see #getUrl() */
	public static final String URL = "url";

	/** @see #getType() */
	public static final String TYPE = "type";

	/** Identifier for the property {@link #getUrl()} in binary format. */
	public static final int URL__ID = 1;

	/** Identifier for the property {@link #getType()} in binary format. */
	public static final int TYPE__ID = 2;

	private String _url = "";

	private Type _type = de.haumacher.phoneblock.index.google.UpdateMessage.Type.URL_UPDATED;

	/**
	 * Creates a {@link UpdateMessage} instance.
	 *
	 * @see #create()
	 */
	protected UpdateMessage() {
		super();
	}

	public final String getUrl() {
		return _url;
	}

	/**
	 * @see #getUrl()
	 */
	public UpdateMessage setUrl(String value) {
		internalSetUrl(value);
		return this;
	}
	/** Internal setter for {@link #getUrl()} without chain call utility. */
	protected final void internalSetUrl(String value) {
		_listener.beforeSet(this, URL, value);
		_url = value;
	}


	public final Type getType() {
		return _type;
	}

	/**
	 * @see #getType()
	 */
	public UpdateMessage setType(Type value) {
		internalSetType(value);
		return this;
	}
	/** Internal setter for {@link #getType()} without chain call utility. */
	protected final void internalSetType(Type value) {
		if (value == null) throw new IllegalArgumentException("Property 'type' cannot be null.");
		_listener.beforeSet(this, TYPE, value);
		_type = value;
	}


	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public UpdateMessage registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public UpdateMessage unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return UPDATE_MESSAGE__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			URL, 
			TYPE));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case URL: return getUrl();
			case TYPE: return getType();
			default: return de.haumacher.msgbuf.observer.Observable.super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case URL: setUrl((String) value); break;
			case TYPE: setType((Type) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static UpdateMessage readUpdateMessage(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		UpdateMessage result = new UpdateMessage();
		in.beginObject();
		result.readFields(in);
		in.endObject();
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		writeContent(out);
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(URL);
		out.value(getUrl());
		out.name(TYPE);
		getType().writeTo(out);
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case URL: setUrl(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TYPE: setType(de.haumacher.phoneblock.index.google.UpdateMessage.Type.readType(in)); break;
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
		out.name(URL__ID);
		out.value(getUrl());
		out.name(TYPE__ID);
		getType().writeTo(out);
	}

	/** Reads a new instance from the given reader. */
	public static UpdateMessage readUpdateMessage(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		UpdateMessage result = new UpdateMessage();
		while (in.hasNext()) {
			int field = in.nextName();
			result.readField(in, field);
		}
		in.endObject();
		return result;
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case URL__ID: setUrl(in.nextString()); break;
			case TYPE__ID: setType(de.haumacher.phoneblock.index.google.UpdateMessage.Type.readType(in)); break;
			default: in.skipValue(); 
		}
	}

}
