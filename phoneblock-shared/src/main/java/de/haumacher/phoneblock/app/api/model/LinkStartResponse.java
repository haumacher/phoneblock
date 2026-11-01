package de.haumacher.phoneblock.app.api.model;

/**
 * Response of <code>POST /api/link/start</code>.
 */
public class LinkStartResponse extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.LinkStartResponse} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.LinkStartResponse create() {
		return new de.haumacher.phoneblock.app.api.model.LinkStartResponse();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.LinkStartResponse} type in JSON format. */
	public static final String LINK_START_RESPONSE__TYPE = "LinkStartResponse";

	/** @see #getDeviceCode() */
	private static final String DEVICE_CODE__PROP = "deviceCode";

	/** @see #getUserCode() */
	private static final String USER_CODE__PROP = "userCode";

	/** @see #getVerifyUrl() */
	private static final String VERIFY_URL__PROP = "verifyUrl";

	/** @see #getInterval() */
	private static final String INTERVAL__PROP = "interval";

	/** @see #getExpiresIn() */
	private static final String EXPIRES_IN__PROP = "expiresIn";

	private String _deviceCode = "";

	private String _userCode = "";

	private String _verifyUrl = "";

	private int _interval = 0;

	private int _expiresIn = 0;

	/**
	 * Creates a {@link LinkStartResponse} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.LinkStartResponse#create()
	 */
	protected LinkStartResponse() {
		super();
	}

	/**
	 * Secret code (256 bit, base64url) the device uses to poll for the result. Never shown to the user.
	 */
	public final String getDeviceCode() {
		return _deviceCode;
	}

	/**
	 * @see #getDeviceCode()
	 */
	public de.haumacher.phoneblock.app.api.model.LinkStartResponse setDeviceCode(String value) {
		internalSetDeviceCode(value);
		return this;
	}

	/** Internal setter for {@link #getDeviceCode()} without chain call utility. */
	protected final void internalSetDeviceCode(String value) {
		_deviceCode = value;
	}

	/**
	 * Code shown to the user in the form <code>XXXX-XXXX</code>, to be compared on the confirmation page.
	 */
	public final String getUserCode() {
		return _userCode;
	}

	/**
	 * @see #getUserCode()
	 */
	public de.haumacher.phoneblock.app.api.model.LinkStartResponse setUserCode(String value) {
		internalSetUserCode(value);
		return this;
	}

	/** Internal setter for {@link #getUserCode()} without chain call utility. */
	protected final void internalSetUserCode(String value) {
		_userCode = value;
	}

	/**
	 * URL of the confirmation page with the user code pre-filled.
	 */
	public final String getVerifyUrl() {
		return _verifyUrl;
	}

	/**
	 * @see #getVerifyUrl()
	 */
	public de.haumacher.phoneblock.app.api.model.LinkStartResponse setVerifyUrl(String value) {
		internalSetVerifyUrl(value);
		return this;
	}

	/** Internal setter for {@link #getVerifyUrl()} without chain call utility. */
	protected final void internalSetVerifyUrl(String value) {
		_verifyUrl = value;
	}

	/**
	 * Minimum number of seconds between two polls.
	 */
	public final int getInterval() {
		return _interval;
	}

	/**
	 * @see #getInterval()
	 */
	public de.haumacher.phoneblock.app.api.model.LinkStartResponse setInterval(int value) {
		internalSetInterval(value);
		return this;
	}

	/** Internal setter for {@link #getInterval()} without chain call utility. */
	protected final void internalSetInterval(int value) {
		_interval = value;
	}

	/**
	 * Number of seconds until the link expires.
	 */
	public final int getExpiresIn() {
		return _expiresIn;
	}

	/**
	 * @see #getExpiresIn()
	 */
	public de.haumacher.phoneblock.app.api.model.LinkStartResponse setExpiresIn(int value) {
		internalSetExpiresIn(value);
		return this;
	}

	/** Internal setter for {@link #getExpiresIn()} without chain call utility. */
	protected final void internalSetExpiresIn(int value) {
		_expiresIn = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.LinkStartResponse readLinkStartResponse(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.LinkStartResponse result = new de.haumacher.phoneblock.app.api.model.LinkStartResponse();
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
		out.name(DEVICE_CODE__PROP);
		out.value(getDeviceCode());
		out.name(USER_CODE__PROP);
		out.value(getUserCode());
		out.name(VERIFY_URL__PROP);
		out.value(getVerifyUrl());
		out.name(INTERVAL__PROP);
		out.value(getInterval());
		out.name(EXPIRES_IN__PROP);
		out.value(getExpiresIn());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case DEVICE_CODE__PROP: setDeviceCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case USER_CODE__PROP: setUserCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case VERIFY_URL__PROP: setVerifyUrl(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case INTERVAL__PROP: setInterval(in.nextInt()); break;
			case EXPIRES_IN__PROP: setExpiresIn(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.LinkStartResponse} type. */
	public static final String LINK_START_RESPONSE__XML_ELEMENT = "link-start-response";

	/** XML attribute or element name of a {@link #getDeviceCode} property. */
	private static final String DEVICE_CODE__XML_ATTR = "device-code";

	/** XML attribute or element name of a {@link #getUserCode} property. */
	private static final String USER_CODE__XML_ATTR = "user-code";

	/** XML attribute or element name of a {@link #getVerifyUrl} property. */
	private static final String VERIFY_URL__XML_ATTR = "verify-url";

	/** XML attribute or element name of a {@link #getInterval} property. */
	private static final String INTERVAL__XML_ATTR = "interval";

	/** XML attribute or element name of a {@link #getExpiresIn} property. */
	private static final String EXPIRES_IN__XML_ATTR = "expires-in";

	@Override
	public String getXmlTagName() {
		return LINK_START_RESPONSE__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(DEVICE_CODE__XML_ATTR, getDeviceCode());
		out.writeAttribute(USER_CODE__XML_ATTR, getUserCode());
		out.writeAttribute(VERIFY_URL__XML_ATTR, getVerifyUrl());
		out.writeAttribute(INTERVAL__XML_ATTR, Integer.toString(getInterval()));
		out.writeAttribute(EXPIRES_IN__XML_ATTR, Integer.toString(getExpiresIn()));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.LinkStartResponse} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static LinkStartResponse readLinkStartResponse_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		LinkStartResponse result = new LinkStartResponse();
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
			case DEVICE_CODE__XML_ATTR: {
				setDeviceCode(value);
				break;
			}
			case USER_CODE__XML_ATTR: {
				setUserCode(value);
				break;
			}
			case VERIFY_URL__XML_ATTR: {
				setVerifyUrl(value);
				break;
			}
			case INTERVAL__XML_ATTR: {
				setInterval(Integer.parseInt(value));
				break;
			}
			case EXPIRES_IN__XML_ATTR: {
				setExpiresIn(Integer.parseInt(value));
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
			case DEVICE_CODE__XML_ATTR: {
				setDeviceCode(in.getElementText());
				break;
			}
			case USER_CODE__XML_ATTR: {
				setUserCode(in.getElementText());
				break;
			}
			case VERIFY_URL__XML_ATTR: {
				setVerifyUrl(in.getElementText());
				break;
			}
			case INTERVAL__XML_ATTR: {
				setInterval(Integer.parseInt(in.getElementText()));
				break;
			}
			case EXPIRES_IN__XML_ATTR: {
				setExpiresIn(Integer.parseInt(in.getElementText()));
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

	/** Creates a new {@link LinkStartResponse} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static LinkStartResponse readLinkStartResponse(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.LinkStartResponse.readLinkStartResponse_XmlContent(in);
	}

}
