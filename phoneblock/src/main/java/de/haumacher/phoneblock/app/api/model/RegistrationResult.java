package de.haumacher.phoneblock.app.api.model;

/**
 * The login data created during registration.
 */
public class RegistrationResult extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link RegistrationResult} instance.
	 */
	public static RegistrationResult create() {
		return new de.haumacher.phoneblock.app.api.model.RegistrationResult();
	}

	/** Identifier for the {@link RegistrationResult} type in JSON format. */
	public static final String REGISTRATION_RESULT__TYPE = "RegistrationResult";

	/** @see #getSession() */
	public static final String SESSION__PROP = "session";

	/** @see #getEmail() */
	public static final String EMAIL__PROP = "email";

	/** @see #getPassword() */
	public static final String PASSWORD__PROP = "password";

	/** Identifier for the property {@link #getSession()} in binary format. */
	static final int SESSION__ID = 1;

	/** Identifier for the property {@link #getEmail()} in binary format. */
	static final int EMAIL__ID = 2;

	/** Identifier for the property {@link #getPassword()} in binary format. */
	static final int PASSWORD__ID = 3;

	private String _session = "";

	private String _email = "";

	private String _password = "";

	/**
	 * Creates a {@link RegistrationResult} instance.
	 *
	 * @see RegistrationResult#create()
	 */
	protected RegistrationResult() {
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
	public RegistrationResult setSession(String value) {
		internalSetSession(value);
		return this;
	}

	/** Internal setter for {@link #getSession()} without chain call utility. */
	protected final void internalSetSession(String value) {
		_listener.beforeSet(this, SESSION__PROP, value);
		_session = value;
	}

	/**
	 * The new user name.
	 */
	public final String getEmail() {
		return _email;
	}

	/**
	 * @see #getEmail()
	 */
	public RegistrationResult setEmail(String value) {
		internalSetEmail(value);
		return this;
	}

	/** Internal setter for {@link #getEmail()} without chain call utility. */
	protected final void internalSetEmail(String value) {
		_listener.beforeSet(this, EMAIL__PROP, value);
		_email = value;
	}

	/**
	 * The user's secure password.
	 */
	public final String getPassword() {
		return _password;
	}

	/**
	 * @see #getPassword()
	 */
	public RegistrationResult setPassword(String value) {
		internalSetPassword(value);
		return this;
	}

	/** Internal setter for {@link #getPassword()} without chain call utility. */
	protected final void internalSetPassword(String value) {
		_listener.beforeSet(this, PASSWORD__PROP, value);
		_password = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public RegistrationResult registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public RegistrationResult unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return REGISTRATION_RESULT__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			SESSION__PROP, 
			EMAIL__PROP, 
			PASSWORD__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case SESSION__PROP: return getSession();
			case EMAIL__PROP: return getEmail();
			case PASSWORD__PROP: return getPassword();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case SESSION__PROP: internalSetSession((String) value); break;
			case EMAIL__PROP: internalSetEmail((String) value); break;
			case PASSWORD__PROP: internalSetPassword((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static RegistrationResult readRegistrationResult(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RegistrationResult result = new de.haumacher.phoneblock.app.api.model.RegistrationResult();
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
		out.name(EMAIL__PROP);
		out.value(getEmail());
		out.name(PASSWORD__PROP);
		out.value(getPassword());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case SESSION__PROP: setSession(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case EMAIL__PROP: setEmail(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PASSWORD__PROP: setPassword(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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
		out.name(EMAIL__ID);
		out.value(getEmail());
		out.name(PASSWORD__ID);
		out.value(getPassword());
	}

	/** Reads a new instance from the given reader. */
	public static RegistrationResult readRegistrationResult(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		RegistrationResult result = de.haumacher.phoneblock.app.api.model.RegistrationResult.readRegistrationResult_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link RegistrationResult} from a polymorphic composition. */
	public static RegistrationResult readRegistrationResult_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RegistrationResult result = new RegistrationResult();
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
			case EMAIL__ID: setEmail(in.nextString()); break;
			case PASSWORD__ID: setPassword(in.nextString()); break;
			default: in.skipValue(); 
		}
	}

}
