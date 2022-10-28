package de.haumacher.phoneblock.app.api.model;

public class RegistrationRequest extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link RegistrationRequest} instance.
	 */
	public static RegistrationRequest create() {
		return new de.haumacher.phoneblock.app.api.model.RegistrationRequest();
	}

	/** Identifier for the {@link RegistrationRequest} type in JSON format. */
	public static final String REGISTRATION_REQUEST__TYPE = "RegistrationRequest";

	/** @see #getSession() */
	public static final String SESSION__PROP = "session";

	/** @see #getAnswer() */
	public static final String ANSWER__PROP = "answer";

	/** @see #getEmail() */
	public static final String EMAIL__PROP = "email";

	/** Identifier for the property {@link #getSession()} in binary format. */
	static final int SESSION__ID = 1;

	/** Identifier for the property {@link #getAnswer()} in binary format. */
	static final int ANSWER__ID = 2;

	/** Identifier for the property {@link #getEmail()} in binary format. */
	static final int EMAIL__ID = 3;

	private String _session = "";

	private String _answer = "";

	private String _email = "";

	/**
	 * Creates a {@link RegistrationRequest} instance.
	 *
	 * @see RegistrationRequest#create()
	 */
	protected RegistrationRequest() {
		super();
	}

	public final String getSession() {
		return _session;
	}

	/**
	 * @see #getSession()
	 */
	public RegistrationRequest setSession(String value) {
		internalSetSession(value);
		return this;
	}

	/** Internal setter for {@link #getSession()} without chain call utility. */
	protected final void internalSetSession(String value) {
		_listener.beforeSet(this, SESSION__PROP, value);
		_session = value;
	}

	public final String getAnswer() {
		return _answer;
	}

	/**
	 * @see #getAnswer()
	 */
	public RegistrationRequest setAnswer(String value) {
		internalSetAnswer(value);
		return this;
	}

	/** Internal setter for {@link #getAnswer()} without chain call utility. */
	protected final void internalSetAnswer(String value) {
		_listener.beforeSet(this, ANSWER__PROP, value);
		_answer = value;
	}

	public final String getEmail() {
		return _email;
	}

	/**
	 * @see #getEmail()
	 */
	public RegistrationRequest setEmail(String value) {
		internalSetEmail(value);
		return this;
	}

	/** Internal setter for {@link #getEmail()} without chain call utility. */
	protected final void internalSetEmail(String value) {
		_listener.beforeSet(this, EMAIL__PROP, value);
		_email = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public RegistrationRequest registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public RegistrationRequest unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return REGISTRATION_REQUEST__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			SESSION__PROP, 
			ANSWER__PROP, 
			EMAIL__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case SESSION__PROP: return getSession();
			case ANSWER__PROP: return getAnswer();
			case EMAIL__PROP: return getEmail();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case SESSION__PROP: internalSetSession((String) value); break;
			case ANSWER__PROP: internalSetAnswer((String) value); break;
			case EMAIL__PROP: internalSetEmail((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static RegistrationRequest readRegistrationRequest(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RegistrationRequest result = new de.haumacher.phoneblock.app.api.model.RegistrationRequest();
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
		out.name(SESSION__PROP);
		out.value(getSession());
		out.name(ANSWER__PROP);
		out.value(getAnswer());
		out.name(EMAIL__PROP);
		out.value(getEmail());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case SESSION__PROP: setSession(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ANSWER__PROP: setAnswer(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case EMAIL__PROP: setEmail(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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
		out.name(SESSION__ID);
		out.value(getSession());
		out.name(ANSWER__ID);
		out.value(getAnswer());
		out.name(EMAIL__ID);
		out.value(getEmail());
	}

	/** Reads a new instance from the given reader. */
	public static RegistrationRequest readRegistrationRequest(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		RegistrationRequest result = de.haumacher.phoneblock.app.api.model.RegistrationRequest.readRegistrationRequest_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link RegistrationRequest} from a polymorphic composition. */
	public static RegistrationRequest readRegistrationRequest_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RegistrationRequest result = new RegistrationRequest();
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
			case SESSION__ID: setSession(in.nextString()); break;
			case ANSWER__ID: setAnswer(in.nextString()); break;
			case EMAIL__ID: setEmail(in.nextString()); break;
			default: in.skipValue(); 
		}
	}

}
