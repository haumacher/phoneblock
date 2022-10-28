package de.haumacher.phoneblock.app.api.model;

/**
 * Information that must be requested to start a registration process.
 */
public class RegistrationChallenge extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link RegistrationChallenge} instance.
	 */
	public static RegistrationChallenge create() {
		return new de.haumacher.phoneblock.app.api.model.RegistrationChallenge();
	}

	/** Identifier for the {@link RegistrationChallenge} type in JSON format. */
	public static final String REGISTRATION_CHALLENGE__TYPE = "RegistrationChallenge";

	/** @see #getSession() */
	public static final String SESSION__PROP = "session";

	/** @see #getCaptcha() */
	public static final String CAPTCHA__PROP = "captcha";

	/** Identifier for the property {@link #getSession()} in binary format. */
	static final int SESSION__ID = 1;

	/** Identifier for the property {@link #getCaptcha()} in binary format. */
	static final int CAPTCHA__ID = 2;

	private String _session = "";

	private String _captcha = "";

	/**
	 * Creates a {@link RegistrationChallenge} instance.
	 *
	 * @see RegistrationChallenge#create()
	 */
	protected RegistrationChallenge() {
		super();
	}

	/**
	 * The registration session ID, must be provided to following calls.
	 */
	public final String getSession() {
		return _session;
	}

	/**
	 * @see #getSession()
	 */
	public RegistrationChallenge setSession(String value) {
		internalSetSession(value);
		return this;
	}

	/** Internal setter for {@link #getSession()} without chain call utility. */
	protected final void internalSetSession(String value) {
		_listener.beforeSet(this, SESSION__PROP, value);
		_session = value;
	}

	/**
	 * A Base64 encoded image hiding some random text. The text must be entered to the {@link RegistrationRequest#getAnswer()} field.
	 */
	public final String getCaptcha() {
		return _captcha;
	}

	/**
	 * @see #getCaptcha()
	 */
	public RegistrationChallenge setCaptcha(String value) {
		internalSetCaptcha(value);
		return this;
	}

	/** Internal setter for {@link #getCaptcha()} without chain call utility. */
	protected final void internalSetCaptcha(String value) {
		_listener.beforeSet(this, CAPTCHA__PROP, value);
		_captcha = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public RegistrationChallenge registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public RegistrationChallenge unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return REGISTRATION_CHALLENGE__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			SESSION__PROP, 
			CAPTCHA__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case SESSION__PROP: return getSession();
			case CAPTCHA__PROP: return getCaptcha();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case SESSION__PROP: internalSetSession((String) value); break;
			case CAPTCHA__PROP: internalSetCaptcha((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static RegistrationChallenge readRegistrationChallenge(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RegistrationChallenge result = new de.haumacher.phoneblock.app.api.model.RegistrationChallenge();
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
		out.name(CAPTCHA__PROP);
		out.value(getCaptcha());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case SESSION__PROP: setSession(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CAPTCHA__PROP: setCaptcha(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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
		out.name(CAPTCHA__ID);
		out.value(getCaptcha());
	}

	/** Reads a new instance from the given reader. */
	public static RegistrationChallenge readRegistrationChallenge(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		RegistrationChallenge result = de.haumacher.phoneblock.app.api.model.RegistrationChallenge.readRegistrationChallenge_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link RegistrationChallenge} from a polymorphic composition. */
	public static RegistrationChallenge readRegistrationChallenge_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RegistrationChallenge result = new RegistrationChallenge();
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
			case CAPTCHA__ID: setCaptcha(in.nextString()); break;
			default: in.skipValue(); 
		}
	}

}
