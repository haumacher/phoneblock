package de.haumacher.phoneblock.app.api.model;

/**
 * The login data created during registration.
 */
public class RegistrationResult extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.RegistrationResult} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.RegistrationResult create() {
		return new de.haumacher.phoneblock.app.api.model.RegistrationResult();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.RegistrationResult} type in JSON format. */
	public static final String REGISTRATION_RESULT__TYPE = "RegistrationResult";

	/** @see #getSession() */
	public static final String SESSION__PROP = "session";

	/** @see #getLogin() */
	public static final String LOGIN__PROP = "login";

	/** @see #getPassword() */
	public static final String PASSWORD__PROP = "password";

	/** Identifier for the property {@link #getSession()} in binary format. */
	static final int SESSION__ID = 1;

	/** Identifier for the property {@link #getLogin()} in binary format. */
	static final int LOGIN__ID = 2;

	/** Identifier for the property {@link #getPassword()} in binary format. */
	static final int PASSWORD__ID = 3;

	private String _session = "";

	private String _login = "";

	private String _password = "";

	/**
	 * Creates a {@link RegistrationResult} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.RegistrationResult#create()
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
	public de.haumacher.phoneblock.app.api.model.RegistrationResult setSession(String value) {
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
	public final String getLogin() {
		return _login;
	}

	/**
	 * @see #getLogin()
	 */
	public de.haumacher.phoneblock.app.api.model.RegistrationResult setLogin(String value) {
		internalSetLogin(value);
		return this;
	}

	/** Internal setter for {@link #getLogin()} without chain call utility. */
	protected final void internalSetLogin(String value) {
		_listener.beforeSet(this, LOGIN__PROP, value);
		_login = value;
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
	public de.haumacher.phoneblock.app.api.model.RegistrationResult setPassword(String value) {
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
	public de.haumacher.phoneblock.app.api.model.RegistrationResult registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.RegistrationResult unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
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
			LOGIN__PROP, 
			PASSWORD__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case SESSION__PROP: return getSession();
			case LOGIN__PROP: return getLogin();
			case PASSWORD__PROP: return getPassword();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case SESSION__PROP: internalSetSession((String) value); break;
			case LOGIN__PROP: internalSetLogin((String) value); break;
			case PASSWORD__PROP: internalSetPassword((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.RegistrationResult readRegistrationResult(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
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
		out.name(LOGIN__PROP);
		out.value(getLogin());
		out.name(PASSWORD__PROP);
		out.value(getPassword());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case SESSION__PROP: setSession(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LOGIN__PROP: setLogin(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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
		out.name(LOGIN__ID);
		out.value(getLogin());
		out.name(PASSWORD__ID);
		out.value(getPassword());
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.RegistrationResult readRegistrationResult(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.app.api.model.RegistrationResult result = de.haumacher.phoneblock.app.api.model.RegistrationResult.readRegistrationResult_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.app.api.model.RegistrationResult} from a polymorphic composition. */
	public static de.haumacher.phoneblock.app.api.model.RegistrationResult readRegistrationResult_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
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
			case LOGIN__ID: setLogin(in.nextString()); break;
			case PASSWORD__ID: setPassword(in.nextString()); break;
			default: in.skipValue(); 
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.RegistrationResult} type. */
	public static final String REGISTRATION_RESULT__XML_ELEMENT = "registration-result";

	/** XML attribute or element name of a {@link #getSession} property. */
	private static final String SESSION__XML_ATTR = "session";

	/** XML attribute or element name of a {@link #getLogin} property. */
	private static final String LOGIN__XML_ATTR = "login";

	/** XML attribute or element name of a {@link #getPassword} property. */
	private static final String PASSWORD__XML_ATTR = "password";

	@Override
	public String getXmlTagName() {
		return REGISTRATION_RESULT__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(SESSION__XML_ATTR, getSession());
		out.writeAttribute(LOGIN__XML_ATTR, getLogin());
		out.writeAttribute(PASSWORD__XML_ATTR, getPassword());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.RegistrationResult} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RegistrationResult readRegistrationResult_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		RegistrationResult result = new RegistrationResult();
		result.readContentXml(in);
		return result;
	}

	/** Reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	protected final void readContentXml(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		for (int n = 0, cnt = in.getAttributeCount(); n < cnt; n++) {
			String name = in.getAttributeLocalName(n);
			String value = in.getAttributeValue(n);

			readFieldXmlAttribute(name, value);
		}
		while (true) {
			int event = in.nextTag();
			if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT) {
				break;
			}
			assert event == javax.xml.stream.XMLStreamConstants.START_ELEMENT;

			String localName = in.getLocalName();
			readFieldXmlElement(in, localName);
		}
	}

	/** Parses the given attribute value and assigns it to the field with the given name. */
	protected void readFieldXmlAttribute(String name, String value) {
		switch (name) {
			case SESSION__XML_ATTR: {
				setSession(value);
				break;
			}
			case LOGIN__XML_ATTR: {
				setLogin(value);
				break;
			}
			case PASSWORD__XML_ATTR: {
				setPassword(value);
				break;
			}
			default: {
				// Skip unknown attribute.
			}
		}
	}

	/** Reads the element under the cursor and assigns its contents to the field with the given name. */
	protected void readFieldXmlElement(javax.xml.stream.XMLStreamReader in, String localName) throws javax.xml.stream.XMLStreamException {
		switch (localName) {
			case SESSION__XML_ATTR: {
				setSession(in.getElementText());
				break;
			}
			case LOGIN__XML_ATTR: {
				setLogin(in.getElementText());
				break;
			}
			case PASSWORD__XML_ATTR: {
				setPassword(in.getElementText());
				break;
			}
			default: {
				internalSkipUntilMatchingEndElement(in);
			}
		}
	}

	protected static final void internalSkipUntilMatchingEndElement(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		int level = 0;
		while (true) {
			switch (in.next()) {
				case javax.xml.stream.XMLStreamConstants.START_ELEMENT: level++; break;
				case javax.xml.stream.XMLStreamConstants.END_ELEMENT: if (level == 0) { return; } else { level--; break; }
			}
		}
	}

	/** Creates a new {@link RegistrationResult} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RegistrationResult readRegistrationResult(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.RegistrationResult.readRegistrationResult_XmlContent(in);
	}

}
