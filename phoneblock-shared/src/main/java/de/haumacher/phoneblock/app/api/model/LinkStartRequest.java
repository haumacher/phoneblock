package de.haumacher.phoneblock.app.api.model;

/**
 * Request of <code>POST /api/link/start</code>: starts a device-code link (#621).
 *
 * <p>
 * Used by a device that cannot receive the redirect of the regular sign-in (the dongle's Linux
 * service behind Home Assistant ingress). The device shows the returned user code, the user
 * confirms it on <code>/link</code>, and the device collects the token with
 * <code>POST /api/link/poll</code>.
 * </p>
 */
public class LinkStartRequest extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.LinkStartRequest} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.LinkStartRequest create() {
		return new de.haumacher.phoneblock.app.api.model.LinkStartRequest();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.LinkStartRequest} type in JSON format. */
	public static final String LINK_START_REQUEST__TYPE = "LinkStartRequest";

	/** @see #getAppId() */
	private static final String APP_ID__PROP = "appId";

	/** @see #getLabel() */
	private static final String LABEL__PROP = "label";

	private String _appId = "";

	private String _label = null;

	/**
	 * Creates a {@link LinkStartRequest} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.LinkStartRequest#create()
	 */
	protected LinkStartRequest() {
		super();
	}

	/**
	 * Identifier of the requesting app, e.g. <code>PhoneBlockDongle</code>. Required.
	 */
	public final String getAppId() {
		return _appId;
	}

	/**
	 * @see #getAppId()
	 */
	public de.haumacher.phoneblock.app.api.model.LinkStartRequest setAppId(String value) {
		internalSetAppId(value);
		return this;
	}

	/** Internal setter for {@link #getAppId()} without chain call utility. */
	protected final void internalSetAppId(String value) {
		_appId = value;
	}

	/**
	 * User-visible device name, shown on the confirmation page and used as token label (at most 64 characters). Defaults to <code>PhoneBlock-Dongle</code>.
	 */
	public final String getLabel() {
		return _label;
	}

	/**
	 * @see #getLabel()
	 */
	public de.haumacher.phoneblock.app.api.model.LinkStartRequest setLabel(String value) {
		internalSetLabel(value);
		return this;
	}

	/** Internal setter for {@link #getLabel()} without chain call utility. */
	protected final void internalSetLabel(String value) {
		_label = value;
	}

	/**
	 * Checks, whether {@link #getLabel()} has a value.
	 */
	public final boolean hasLabel() {
		return _label != null;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.LinkStartRequest readLinkStartRequest(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.LinkStartRequest result = new de.haumacher.phoneblock.app.api.model.LinkStartRequest();
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
		out.name(APP_ID__PROP);
		out.value(getAppId());
		if (hasLabel()) {
			out.name(LABEL__PROP);
			out.value(getLabel());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case APP_ID__PROP: setAppId(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LABEL__PROP: setLabel(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.LinkStartRequest} type. */
	public static final String LINK_START_REQUEST__XML_ELEMENT = "link-start-request";

	/** XML attribute or element name of a {@link #getAppId} property. */
	private static final String APP_ID__XML_ATTR = "app-id";

	/** XML attribute or element name of a {@link #getLabel} property. */
	private static final String LABEL__XML_ATTR = "label";

	@Override
	public String getXmlTagName() {
		return LINK_START_REQUEST__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(APP_ID__XML_ATTR, getAppId());
		out.writeAttribute(LABEL__XML_ATTR, getLabel());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.LinkStartRequest} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static LinkStartRequest readLinkStartRequest_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		LinkStartRequest result = new LinkStartRequest();
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
			case APP_ID__XML_ATTR: {
				setAppId(value);
				break;
			}
			case LABEL__XML_ATTR: {
				setLabel(value);
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
			case APP_ID__XML_ATTR: {
				setAppId(in.getElementText());
				break;
			}
			case LABEL__XML_ATTR: {
				setLabel(in.getElementText());
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

	/** Creates a new {@link LinkStartRequest} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static LinkStartRequest readLinkStartRequest(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.LinkStartRequest.readLinkStartRequest_XmlContent(in);
	}

}
