package de.haumacher.phoneblock.app.api.model;

/**
 * Response of <code>POST /api/link/poll</code>.
 */
public class LinkPollResponse extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.LinkPollResponse} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.LinkPollResponse create() {
		return new de.haumacher.phoneblock.app.api.model.LinkPollResponse();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.LinkPollResponse} type in JSON format. */
	public static final String LINK_POLL_RESPONSE__TYPE = "LinkPollResponse";

	/** @see #getStatus() */
	private static final String STATUS__PROP = "status";

	/** @see #getToken() */
	private static final String TOKEN__PROP = "token";

	private String _status = "";

	private String _token = null;

	/**
	 * Creates a {@link LinkPollResponse} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.LinkPollResponse#create()
	 */
	protected LinkPollResponse() {
		super();
	}

	/**
	 * One of <code>pending</code>, <code>slow_down</code>, <code>approved</code>, <code>denied</code>, <code>expired</code>.
	 */
	public final String getStatus() {
		return _status;
	}

	/**
	 * @see #getStatus()
	 */
	public de.haumacher.phoneblock.app.api.model.LinkPollResponse setStatus(String value) {
		internalSetStatus(value);
		return this;
	}

	/** Internal setter for {@link #getStatus()} without chain call utility. */
	protected final void internalSetStatus(String value) {
		_status = value;
	}

	/**
	 * The new API token, only set with status <code>approved</code>.
	 */
	public final String getToken() {
		return _token;
	}

	/**
	 * @see #getToken()
	 */
	public de.haumacher.phoneblock.app.api.model.LinkPollResponse setToken(String value) {
		internalSetToken(value);
		return this;
	}

	/** Internal setter for {@link #getToken()} without chain call utility. */
	protected final void internalSetToken(String value) {
		_token = value;
	}

	/**
	 * Checks, whether {@link #getToken()} has a value.
	 */
	public final boolean hasToken() {
		return _token != null;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.LinkPollResponse readLinkPollResponse(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.LinkPollResponse result = new de.haumacher.phoneblock.app.api.model.LinkPollResponse();
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
		out.name(STATUS__PROP);
		out.value(getStatus());
		if (hasToken()) {
			out.name(TOKEN__PROP);
			out.value(getToken());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case STATUS__PROP: setStatus(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TOKEN__PROP: setToken(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.LinkPollResponse} type. */
	public static final String LINK_POLL_RESPONSE__XML_ELEMENT = "link-poll-response";

	/** XML attribute or element name of a {@link #getStatus} property. */
	private static final String STATUS__XML_ATTR = "status";

	/** XML attribute or element name of a {@link #getToken} property. */
	private static final String TOKEN__XML_ATTR = "token";

	@Override
	public String getXmlTagName() {
		return LINK_POLL_RESPONSE__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(STATUS__XML_ATTR, getStatus());
		out.writeAttribute(TOKEN__XML_ATTR, getToken());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.LinkPollResponse} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static LinkPollResponse readLinkPollResponse_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		LinkPollResponse result = new LinkPollResponse();
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
			case STATUS__XML_ATTR: {
				setStatus(value);
				break;
			}
			case TOKEN__XML_ATTR: {
				setToken(value);
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
			case STATUS__XML_ATTR: {
				setStatus(in.getElementText());
				break;
			}
			case TOKEN__XML_ATTR: {
				setToken(in.getElementText());
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

	/** Creates a new {@link LinkPollResponse} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static LinkPollResponse readLinkPollResponse(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.LinkPollResponse.readLinkPollResponse_XmlContent(in);
	}

}
