package de.haumacher.phoneblock.app.api.model;

/**
 * The completion of the registration.
 */
public class RegistrationCompletion extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.RegistrationCompletion} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.RegistrationCompletion create() {
		return new de.haumacher.phoneblock.app.api.model.RegistrationCompletion();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.RegistrationCompletion} type in JSON format. */
	public static final String REGISTRATION_COMPLETION__TYPE = "RegistrationCompletion";

	/** @see #getSession() */
	public static final String SESSION__PROP = "session";

	/** @see #getCode() */
	public static final String CODE__PROP = "code";

	private String _session = "";

	private String _code = "";

	/**
	 * Creates a {@link RegistrationCompletion} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.RegistrationCompletion#create()
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
	public de.haumacher.phoneblock.app.api.model.RegistrationCompletion setSession(String value) {
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
	public de.haumacher.phoneblock.app.api.model.RegistrationCompletion setCode(String value) {
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
	public de.haumacher.phoneblock.app.api.model.RegistrationCompletion registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.RegistrationCompletion unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
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
	public static de.haumacher.phoneblock.app.api.model.RegistrationCompletion readRegistrationCompletion(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
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

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.RegistrationCompletion} type. */
	public static final String REGISTRATION_COMPLETION__XML_ELEMENT = "registration-completion";

	/** XML attribute or element name of a {@link #getSession} property. */
	private static final String SESSION__XML_ATTR = "session";

	/** XML attribute or element name of a {@link #getCode} property. */
	private static final String CODE__XML_ATTR = "code";

	@Override
	public String getXmlTagName() {
		return REGISTRATION_COMPLETION__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(SESSION__XML_ATTR, getSession());
		out.writeAttribute(CODE__XML_ATTR, getCode());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.RegistrationCompletion} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RegistrationCompletion readRegistrationCompletion_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		RegistrationCompletion result = new RegistrationCompletion();
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
			case SESSION__XML_ATTR: {
				setSession(in.getElementText());
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

	/** Creates a new {@link RegistrationCompletion} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RegistrationCompletion readRegistrationCompletion(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.RegistrationCompletion.readRegistrationCompletion_XmlContent(in);
	}

}
