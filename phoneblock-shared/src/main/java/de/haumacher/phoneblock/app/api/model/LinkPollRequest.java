package de.haumacher.phoneblock.app.api.model;

/**
 * Request of <code>POST /api/link/poll</code>.
 */
public class LinkPollRequest extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.LinkPollRequest} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.LinkPollRequest create() {
		return new de.haumacher.phoneblock.app.api.model.LinkPollRequest();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.LinkPollRequest} type in JSON format. */
	public static final String LINK_POLL_REQUEST__TYPE = "LinkPollRequest";

	/** @see #getDeviceCode() */
	private static final String DEVICE_CODE__PROP = "deviceCode";

	private String _deviceCode = "";

	/**
	 * Creates a {@link LinkPollRequest} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.LinkPollRequest#create()
	 */
	protected LinkPollRequest() {
		super();
	}

	/**
	 * The device code from {@link LinkStartResponse#getDeviceCode()}.
	 */
	public final String getDeviceCode() {
		return _deviceCode;
	}

	/**
	 * @see #getDeviceCode()
	 */
	public de.haumacher.phoneblock.app.api.model.LinkPollRequest setDeviceCode(String value) {
		internalSetDeviceCode(value);
		return this;
	}

	/** Internal setter for {@link #getDeviceCode()} without chain call utility. */
	protected final void internalSetDeviceCode(String value) {
		_deviceCode = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.LinkPollRequest readLinkPollRequest(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.LinkPollRequest result = new de.haumacher.phoneblock.app.api.model.LinkPollRequest();
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
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case DEVICE_CODE__PROP: setDeviceCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.LinkPollRequest} type. */
	public static final String LINK_POLL_REQUEST__XML_ELEMENT = "link-poll-request";

	/** XML attribute or element name of a {@link #getDeviceCode} property. */
	private static final String DEVICE_CODE__XML_ATTR = "device-code";

	@Override
	public String getXmlTagName() {
		return LINK_POLL_REQUEST__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(DEVICE_CODE__XML_ATTR, getDeviceCode());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.LinkPollRequest} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static LinkPollRequest readLinkPollRequest_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		LinkPollRequest result = new LinkPollRequest();
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

	/** Creates a new {@link LinkPollRequest} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static LinkPollRequest readLinkPollRequest(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.LinkPollRequest.readLinkPollRequest_XmlContent(in);
	}

}
