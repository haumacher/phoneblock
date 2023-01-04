package de.haumacher.phoneblock.index.google.impl;

/**
 * Message sent to the Google update service.
 */
public class UpdateMessage_Impl extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.phoneblock.index.google.UpdateMessage {

	private String _url = "";

	private de.haumacher.phoneblock.index.google.UpdateMessage.Type _type = de.haumacher.phoneblock.index.google.UpdateMessage.Type.URL_UPDATED;

	/**
	 * Creates a {@link UpdateMessage_Impl} instance.
	 *
	 * @see de.haumacher.phoneblock.index.google.UpdateMessage#create()
	 */
	public UpdateMessage_Impl() {
		super();
	}

	@Override
	public final String getUrl() {
		return _url;
	}

	@Override
	public de.haumacher.phoneblock.index.google.UpdateMessage setUrl(String value) {
		internalSetUrl(value);
		return this;
	}

	/** Internal setter for {@link #getUrl()} without chain call utility. */
	protected final void internalSetUrl(String value) {
		_listener.beforeSet(this, URL__PROP, value);
		_url = value;
	}

	@Override
	public final de.haumacher.phoneblock.index.google.UpdateMessage.Type getType() {
		return _type;
	}

	@Override
	public de.haumacher.phoneblock.index.google.UpdateMessage setType(de.haumacher.phoneblock.index.google.UpdateMessage.Type value) {
		internalSetType(value);
		return this;
	}

	/** Internal setter for {@link #getType()} without chain call utility. */
	protected final void internalSetType(de.haumacher.phoneblock.index.google.UpdateMessage.Type value) {
		if (value == null) throw new IllegalArgumentException("Property 'type' cannot be null.");
		_listener.beforeSet(this, TYPE__PROP, value);
		_type = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.index.google.UpdateMessage registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.index.google.UpdateMessage unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
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
			URL__PROP, 
			TYPE__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case URL__PROP: return getUrl();
			case TYPE__PROP: return getType();
			default: return de.haumacher.phoneblock.index.google.UpdateMessage.super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case URL__PROP: internalSetUrl((String) value); break;
			case TYPE__PROP: internalSetType((de.haumacher.phoneblock.index.google.UpdateMessage.Type) value); break;
		}
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		writeContent(out);
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(URL__PROP);
		out.value(getUrl());
		out.name(TYPE__PROP);
		getType().writeTo(out);
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case URL__PROP: setUrl(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TYPE__PROP: setType(de.haumacher.phoneblock.index.google.UpdateMessage.Type.readType(in)); break;
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

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.index.google.UpdateMessage} from a polymorphic composition. */
	public static de.haumacher.phoneblock.index.google.UpdateMessage readUpdateMessage_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.index.google.impl.UpdateMessage_Impl result = new UpdateMessage_Impl();
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
			case URL__ID: setUrl(in.nextString()); break;
			case TYPE__ID: setType(de.haumacher.phoneblock.index.google.UpdateMessage.Type.readType(in)); break;
			default: in.skipValue(); 
		}
	}

}
