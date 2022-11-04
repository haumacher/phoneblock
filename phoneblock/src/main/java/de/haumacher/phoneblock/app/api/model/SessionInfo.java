package de.haumacher.phoneblock.app.api.model;

/**
 * Internal data that is kept between registration requests on the server.
 */
public class SessionInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link SessionInfo} instance.
	 */
	public static SessionInfo create() {
		return new de.haumacher.phoneblock.app.api.model.SessionInfo();
	}

	/** Identifier for the {@link SessionInfo} type in JSON format. */
	public static final String SESSION_INFO__TYPE = "SessionInfo";

	/** @see #getCreated() */
	public static final String CREATED__PROP = "created";

	/** @see #getSession() */
	public static final String SESSION__PROP = "session";

	/** @see #getEmail() */
	public static final String EMAIL__PROP = "email";

	/** @see #getAnswer() */
	public static final String ANSWER__PROP = "answer";

	/** @see #getCode() */
	public static final String CODE__PROP = "code";

	/** Identifier for the property {@link #getCreated()} in binary format. */
	static final int CREATED__ID = 1;

	/** Identifier for the property {@link #getSession()} in binary format. */
	static final int SESSION__ID = 2;

	/** Identifier for the property {@link #getEmail()} in binary format. */
	static final int EMAIL__ID = 3;

	/** Identifier for the property {@link #getAnswer()} in binary format. */
	static final int ANSWER__ID = 4;

	/** Identifier for the property {@link #getCode()} in binary format. */
	static final int CODE__ID = 5;

	private long _created = 0L;

	private String _session = "";

	private String _email = "";

	private String _answer = "";

	private String _code = "";

	/**
	 * Creates a {@link SessionInfo} instance.
	 *
	 * @see SessionInfo#create()
	 */
	protected SessionInfo() {
		super();
	}

	/**
	 * Time when the registration process was started by retrieving the {@link RegistrationChallenge}.
	 */
	public final long getCreated() {
		return _created;
	}

	/**
	 * @see #getCreated()
	 */
	public SessionInfo setCreated(long value) {
		internalSetCreated(value);
		return this;
	}

	/** Internal setter for {@link #getCreated()} without chain call utility. */
	protected final void internalSetCreated(long value) {
		_listener.beforeSet(this, CREATED__PROP, value);
		_created = value;
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
	public SessionInfo setSession(String value) {
		internalSetSession(value);
		return this;
	}

	/** Internal setter for {@link #getSession()} without chain call utility. */
	protected final void internalSetSession(String value) {
		_listener.beforeSet(this, SESSION__PROP, value);
		_session = value;
	}

	/**
	 * The e-mail address of the user to register
	 */
	public final String getEmail() {
		return _email;
	}

	/**
	 * @see #getEmail()
	 */
	public SessionInfo setEmail(String value) {
		internalSetEmail(value);
		return this;
	}

	/** Internal setter for {@link #getEmail()} without chain call utility. */
	protected final void internalSetEmail(String value) {
		_listener.beforeSet(this, EMAIL__PROP, value);
		_email = value;
	}

	/**
	 * The expected answer to the captcha.
	 */
	public final String getAnswer() {
		return _answer;
	}

	/**
	 * @see #getAnswer()
	 */
	public SessionInfo setAnswer(String value) {
		internalSetAnswer(value);
		return this;
	}

	/** Internal setter for {@link #getAnswer()} without chain call utility. */
	protected final void internalSetAnswer(String value) {
		_listener.beforeSet(this, ANSWER__PROP, value);
		_answer = value;
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
	public SessionInfo setCode(String value) {
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
	public SessionInfo registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public SessionInfo unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return SESSION_INFO__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			CREATED__PROP, 
			SESSION__PROP, 
			EMAIL__PROP, 
			ANSWER__PROP, 
			CODE__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case CREATED__PROP: return getCreated();
			case SESSION__PROP: return getSession();
			case EMAIL__PROP: return getEmail();
			case ANSWER__PROP: return getAnswer();
			case CODE__PROP: return getCode();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case CREATED__PROP: internalSetCreated((long) value); break;
			case SESSION__PROP: internalSetSession((String) value); break;
			case EMAIL__PROP: internalSetEmail((String) value); break;
			case ANSWER__PROP: internalSetAnswer((String) value); break;
			case CODE__PROP: internalSetCode((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static SessionInfo readSessionInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.SessionInfo result = new de.haumacher.phoneblock.app.api.model.SessionInfo();
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
		out.name(CREATED__PROP);
		out.value(getCreated());
		out.name(SESSION__PROP);
		out.value(getSession());
		out.name(EMAIL__PROP);
		out.value(getEmail());
		out.name(ANSWER__PROP);
		out.value(getAnswer());
		out.name(CODE__PROP);
		out.value(getCode());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case CREATED__PROP: setCreated(in.nextLong()); break;
			case SESSION__PROP: setSession(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case EMAIL__PROP: setEmail(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ANSWER__PROP: setAnswer(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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
		out.name(CREATED__ID);
		out.value(getCreated());
		out.name(SESSION__ID);
		out.value(getSession());
		out.name(EMAIL__ID);
		out.value(getEmail());
		out.name(ANSWER__ID);
		out.value(getAnswer());
		out.name(CODE__ID);
		out.value(getCode());
	}

	/** Reads a new instance from the given reader. */
	public static SessionInfo readSessionInfo(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		SessionInfo result = de.haumacher.phoneblock.app.api.model.SessionInfo.readSessionInfo_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link SessionInfo} from a polymorphic composition. */
	public static SessionInfo readSessionInfo_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.SessionInfo result = new SessionInfo();
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
			case CREATED__ID: setCreated(in.nextLong()); break;
			case SESSION__ID: setSession(in.nextString()); break;
			case EMAIL__ID: setEmail(in.nextString()); break;
			case ANSWER__ID: setAnswer(in.nextString()); break;
			case CODE__ID: setCode(in.nextString()); break;
			default: in.skipValue(); 
		}
	}

	/** XML element name representing a {@link SessionInfo} type. */
	public static final String SESSION_INFO__XML_ELEMENT = "session-info";

	/** XML attribute or element name of a {@link #getCreated} property. */
	private static final String CREATED__XML_ATTR = "created";

	/** XML attribute or element name of a {@link #getSession} property. */
	private static final String SESSION__XML_ATTR = "session";

	/** XML attribute or element name of a {@link #getEmail} property. */
	private static final String EMAIL__XML_ATTR = "email";

	/** XML attribute or element name of a {@link #getAnswer} property. */
	private static final String ANSWER__XML_ATTR = "answer";

	/** XML attribute or element name of a {@link #getCode} property. */
	private static final String CODE__XML_ATTR = "code";

	@Override
	public String getXmlTagName() {
		return SESSION_INFO__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(CREATED__XML_ATTR, Long.toString(getCreated()));
		out.writeAttribute(SESSION__XML_ATTR, getSession());
		out.writeAttribute(EMAIL__XML_ATTR, getEmail());
		out.writeAttribute(ANSWER__XML_ATTR, getAnswer());
		out.writeAttribute(CODE__XML_ATTR, getCode());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
	}

	/** Creates a new {@link SessionInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static SessionInfo readSessionInfo_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		SessionInfo result = new SessionInfo();
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
			case CREATED__XML_ATTR: {
				setCreated(Long.parseLong(value));
				break;
			}
			case SESSION__XML_ATTR: {
				setSession(value);
				break;
			}
			case EMAIL__XML_ATTR: {
				setEmail(value);
				break;
			}
			case ANSWER__XML_ATTR: {
				setAnswer(value);
				break;
			}
			case CODE__XML_ATTR: {
				setCode(value);
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
			case CREATED__XML_ATTR: {
				setCreated(Long.parseLong(in.getElementText()));
				break;
			}
			case SESSION__XML_ATTR: {
				setSession(in.getElementText());
				break;
			}
			case EMAIL__XML_ATTR: {
				setEmail(in.getElementText());
				break;
			}
			case ANSWER__XML_ATTR: {
				setAnswer(in.getElementText());
				break;
			}
			case CODE__XML_ATTR: {
				setCode(in.getElementText());
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

	/** Creates a new {@link SessionInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static SessionInfo readSessionInfo(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.SessionInfo.readSessionInfo_XmlContent(in);
	}

}
