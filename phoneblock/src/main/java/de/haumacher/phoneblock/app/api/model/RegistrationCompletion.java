package de.haumacher.phoneblock.app.api.model;

/**
 * The completion of the registration.
 */
public class RegistrationCompletion extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link RegistrationCompletion} instance.
	 */
	public static RegistrationCompletion create() {
		return new de.haumacher.phoneblock.app.api.model.RegistrationCompletion();
	}

	/** Identifier for the {@link RegistrationCompletion} type in JSON format. */
	public static final String REGISTRATION_COMPLETION__TYPE = "RegistrationCompletion";

	/** @see #getSession() */
	public static final String SESSION__PROP = "session";

	/** @see #getCode() */
	public static final String CODE__PROP = "code";

	/** Identifier for the property {@link #getSession()} in binary format. */
	static final int SESSION__ID = 1;

	/** Identifier for the property {@link #getCode()} in binary format. */
	static final int CODE__ID = 2;

	private String _session = "";

	private String _code = "";

	/**
	 * Creates a {@link RegistrationCompletion} instance.
	 *
	 * @see RegistrationCompletion#create()
	 */
	protected RegistrationCompletion() {
		super();
	}

	/**
	 * The registration session ID given in {@link RegistrationChallenge#getSession()}.
	 */
	public final String getSession() {
		return _session;
	}

	/**
	 * @see #getSession()
	 */
	public RegistrationCompletion setSession(String value) {
		internalSetSession(value);
		return this;
	}

	/** Internal setter for {@link #getSession()} without chain call utility. */
	protected final void internalSetSession(String value) {
		_listener.beforeSet(this, SESSION__PROP, value);
		_session = value;
	}

	/**
	 * The code that was sent to the user's e-mail address.
	 */
	public final String getCode() {
		return _code;
	}

	/**
	 * @see #getCode()
	 */
	public RegistrationCompletion setCode(String value) {
		internalSetCode(value);
		return this;
	}

	/** Internal setter for {@link #getCode()} without chain call utility. */
	protected final void internalSetCode(String value) {
		_listener.beforeSet(this, CODE__PROP, value);
		_code = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public RegistrationCompletion registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public RegistrationCompletion unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return REGISTRATION_COMPLETION__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			SESSION__PROP, 
			CODE__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case SESSION__PROP: return getSession();
			case CODE__PROP: return getCode();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case SESSION__PROP: internalSetSession((String) value); break;
			case CODE__PROP: internalSetCode((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static RegistrationCompletion readRegistrationCompletion(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RegistrationCompletion result = new de.haumacher.phoneblock.app.api.model.RegistrationCompletion();
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
		out.name(CODE__PROP);
		out.value(getCode());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case SESSION__PROP: setSession(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CODE__PROP: setCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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
		out.name(CODE__ID);
		out.value(getCode());
	}

	/** Reads a new instance from the given reader. */
	public static RegistrationCompletion readRegistrationCompletion(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		RegistrationCompletion result = de.haumacher.phoneblock.app.api.model.RegistrationCompletion.readRegistrationCompletion_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link RegistrationCompletion} from a polymorphic composition. */
	public static RegistrationCompletion readRegistrationCompletion_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RegistrationCompletion result = new RegistrationCompletion();
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
			case CODE__ID: setCode(in.nextString()); break;
			default: in.skipValue(); 
		}
	}

}
