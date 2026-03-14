package de.haumacher.phoneblock.analysis;

public class AreaCode extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.analysis.AreaCode} instance.
	 */
	public static de.haumacher.phoneblock.analysis.AreaCode create() {
		return new de.haumacher.phoneblock.analysis.AreaCode();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.analysis.AreaCode} type in JSON format. */
	public static final String AREA_CODE__TYPE = "AreaCode";

	/** @see #getPhoneAreaCode() */
	private static final String PHONE_AREA_CODE__PROP = "phone_area_code";

	/** @see #getCity() */
	private static final String CITY__PROP = "city";

	/** @see #isActive() */
	private static final String ACTIVE__PROP = "active";

	private String _phoneAreaCode = "";

	private String _city = "";

	private boolean _active = false;

	/**
	 * Creates a {@link AreaCode} instance.
	 *
	 * @see de.haumacher.phoneblock.analysis.AreaCode#create()
	 */
	protected AreaCode() {
		super();
	}

	public final String getPhoneAreaCode() {
		return _phoneAreaCode;
	}

	/**
	 * @see #getPhoneAreaCode()
	 */
	public de.haumacher.phoneblock.analysis.AreaCode setPhoneAreaCode(String value) {
		internalSetPhoneAreaCode(value);
		return this;
	}

	/** Internal setter for {@link #getPhoneAreaCode()} without chain call utility. */
	protected final void internalSetPhoneAreaCode(String value) {
		_phoneAreaCode = value;
	}

	public final String getCity() {
		return _city;
	}

	/**
	 * @see #getCity()
	 */
	public de.haumacher.phoneblock.analysis.AreaCode setCity(String value) {
		internalSetCity(value);
		return this;
	}

	/** Internal setter for {@link #getCity()} without chain call utility. */
	protected final void internalSetCity(String value) {
		_city = value;
	}

	public final boolean isActive() {
		return _active;
	}

	/**
	 * @see #isActive()
	 */
	public de.haumacher.phoneblock.analysis.AreaCode setActive(boolean value) {
		internalSetActive(value);
		return this;
	}

	/** Internal setter for {@link #isActive()} without chain call utility. */
	protected final void internalSetActive(boolean value) {
		_active = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.analysis.AreaCode readAreaCode(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.analysis.AreaCode result = new de.haumacher.phoneblock.analysis.AreaCode();
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
		out.name(PHONE_AREA_CODE__PROP);
		out.value(getPhoneAreaCode());
		out.name(CITY__PROP);
		out.value(getCity());
		out.name(ACTIVE__PROP);
		out.value(isActive());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE_AREA_CODE__PROP: setPhoneAreaCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CITY__PROP: setCity(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ACTIVE__PROP: setActive(in.nextBoolean()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.analysis.AreaCode} type. */
	public static final String AREA_CODE__XML_ELEMENT = "area-code";

	/** XML attribute or element name of a {@link #getPhoneAreaCode} property. */
	private static final String PHONE_AREA_CODE__XML_ATTR = "phone-area-code";

	/** XML attribute or element name of a {@link #getCity} property. */
	private static final String CITY__XML_ATTR = "city";

	/** XML attribute or element name of a {@link #isActive} property. */
	private static final String ACTIVE__XML_ATTR = "active";

	@Override
	public String getXmlTagName() {
		return AREA_CODE__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(PHONE_AREA_CODE__XML_ATTR, getPhoneAreaCode());
		out.writeAttribute(CITY__XML_ATTR, getCity());
		out.writeAttribute(ACTIVE__XML_ATTR, Boolean.toString(isActive()));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.analysis.AreaCode} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AreaCode readAreaCode_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		AreaCode result = new AreaCode();
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
			case PHONE_AREA_CODE__XML_ATTR: {
				setPhoneAreaCode(value);
				break;
			}
			case CITY__XML_ATTR: {
				setCity(value);
				break;
			}
			case ACTIVE__XML_ATTR: {
				setActive(Boolean.parseBoolean(value));
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
			case PHONE_AREA_CODE__XML_ATTR: {
				setPhoneAreaCode(in.getElementText());
				break;
			}
			case CITY__XML_ATTR: {
				setCity(in.getElementText());
				break;
			}
			case ACTIVE__XML_ATTR: {
				setActive(Boolean.parseBoolean(in.getElementText()));
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

	/** Creates a new {@link AreaCode} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AreaCode readAreaCode(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.analysis.AreaCode.readAreaCode_XmlContent(in);
	}

}
