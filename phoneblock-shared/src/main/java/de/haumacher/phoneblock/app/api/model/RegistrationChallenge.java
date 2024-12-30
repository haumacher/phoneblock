package de.haumacher.phoneblock.app.api.model;

/**
 * Information that must be requested to start a registration process.
 */
public class RegistrationChallenge extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.RegistrationChallenge} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.RegistrationChallenge create() {
		return new de.haumacher.phoneblock.app.api.model.RegistrationChallenge();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.RegistrationChallenge} type in JSON format. */
	public static final String REGISTRATION_CHALLENGE__TYPE = "RegistrationChallenge";

	/** @see #getSession() */
	public static final String SESSION__PROP = "session";

	/** @see #getCaptcha() */
	public static final String CAPTCHA__PROP = "captcha";

	private String _session = "";

	private String _captcha = "";

	/**
	 * Creates a {@link RegistrationChallenge} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.RegistrationChallenge#create()
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
	public de.haumacher.phoneblock.app.api.model.RegistrationChallenge setSession(String value) {
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
	public de.haumacher.phoneblock.app.api.model.RegistrationChallenge setCaptcha(String value) {
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
	public de.haumacher.phoneblock.app.api.model.RegistrationChallenge registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.RegistrationChallenge unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
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
	public static de.haumacher.phoneblock.app.api.model.RegistrationChallenge readRegistrationChallenge(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
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

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.RegistrationChallenge} type. */
	public static final String REGISTRATION_CHALLENGE__XML_ELEMENT = "registration-challenge";

	/** XML attribute or element name of a {@link #getSession} property. */
	private static final String SESSION__XML_ATTR = "session";

	/** XML attribute or element name of a {@link #getCaptcha} property. */
	private static final String CAPTCHA__XML_ATTR = "captcha";

	@Override
	public String getXmlTagName() {
		return REGISTRATION_CHALLENGE__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(SESSION__XML_ATTR, getSession());
		out.writeAttribute(CAPTCHA__XML_ATTR, getCaptcha());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.RegistrationChallenge} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RegistrationChallenge readRegistrationChallenge_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		RegistrationChallenge result = new RegistrationChallenge();
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
			case CAPTCHA__XML_ATTR: {
				setCaptcha(value);
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
			case CAPTCHA__XML_ATTR: {
				setCaptcha(in.getElementText());
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

	/** Creates a new {@link RegistrationChallenge} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RegistrationChallenge readRegistrationChallenge(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.RegistrationChallenge.readRegistrationChallenge_XmlContent(in);
	}

}
