package de.haumacher.phoneblock.analysis.model;

public class NationalDestinationCode extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.analysis.model.NationalDestinationCode} instance.
	 */
	public static de.haumacher.phoneblock.analysis.model.NationalDestinationCode create() {
		return new de.haumacher.phoneblock.analysis.model.NationalDestinationCode();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.analysis.model.NationalDestinationCode} type in JSON format. */
	public static final String NATIONAL_DESTINATION_CODE__TYPE = "NationalDestinationCode";

	/** @see #getPrefix() */
	private static final String PREFIX__PROP = "prefix";

	/** @see #getMaxDigits() */
	private static final String MAX_DIGITS__PROP = "maxDigits";

	/** @see #getMinDigits() */
	private static final String MIN_DIGITS__PROP = "minDigits";

	/** @see #getUsage() */
	private static final String USAGE__PROP = "usage";

	/** @see #getInfo() */
	private static final String INFO__PROP = "info";

	private String _prefix = "";

	private int _maxDigits = 0;

	private int _minDigits = 0;

	private String _usage = "";

	private String _info = "";

	/**
	 * Creates a {@link NationalDestinationCode} instance.
	 *
	 * @see de.haumacher.phoneblock.analysis.model.NationalDestinationCode#create()
	 */
	protected NationalDestinationCode() {
		super();
	}

	public final String getPrefix() {
		return _prefix;
	}

	/**
	 * @see #getPrefix()
	 */
	public de.haumacher.phoneblock.analysis.model.NationalDestinationCode setPrefix(String value) {
		internalSetPrefix(value);
		return this;
	}

	/** Internal setter for {@link #getPrefix()} without chain call utility. */
	protected final void internalSetPrefix(String value) {
		_prefix = value;
	}

	public final int getMaxDigits() {
		return _maxDigits;
	}

	/**
	 * @see #getMaxDigits()
	 */
	public de.haumacher.phoneblock.analysis.model.NationalDestinationCode setMaxDigits(int value) {
		internalSetMaxDigits(value);
		return this;
	}

	/** Internal setter for {@link #getMaxDigits()} without chain call utility. */
	protected final void internalSetMaxDigits(int value) {
		_maxDigits = value;
	}

	public final int getMinDigits() {
		return _minDigits;
	}

	/**
	 * @see #getMinDigits()
	 */
	public de.haumacher.phoneblock.analysis.model.NationalDestinationCode setMinDigits(int value) {
		internalSetMinDigits(value);
		return this;
	}

	/** Internal setter for {@link #getMinDigits()} without chain call utility. */
	protected final void internalSetMinDigits(int value) {
		_minDigits = value;
	}

	public final String getUsage() {
		return _usage;
	}

	/**
	 * @see #getUsage()
	 */
	public de.haumacher.phoneblock.analysis.model.NationalDestinationCode setUsage(String value) {
		internalSetUsage(value);
		return this;
	}

	/** Internal setter for {@link #getUsage()} without chain call utility. */
	protected final void internalSetUsage(String value) {
		_usage = value;
	}

	public final String getInfo() {
		return _info;
	}

	/**
	 * @see #getInfo()
	 */
	public de.haumacher.phoneblock.analysis.model.NationalDestinationCode setInfo(String value) {
		internalSetInfo(value);
		return this;
	}

	/** Internal setter for {@link #getInfo()} without chain call utility. */
	protected final void internalSetInfo(String value) {
		_info = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.analysis.model.NationalDestinationCode readNationalDestinationCode(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.analysis.model.NationalDestinationCode result = new de.haumacher.phoneblock.analysis.model.NationalDestinationCode();
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
		out.name(PREFIX__PROP);
		out.value(getPrefix());
		out.name(MAX_DIGITS__PROP);
		out.value(getMaxDigits());
		out.name(MIN_DIGITS__PROP);
		out.value(getMinDigits());
		out.name(USAGE__PROP);
		out.value(getUsage());
		out.name(INFO__PROP);
		out.value(getInfo());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PREFIX__PROP: setPrefix(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MAX_DIGITS__PROP: setMaxDigits(in.nextInt()); break;
			case MIN_DIGITS__PROP: setMinDigits(in.nextInt()); break;
			case USAGE__PROP: setUsage(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case INFO__PROP: setInfo(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.analysis.model.NationalDestinationCode} type. */
	public static final String NATIONAL_DESTINATION_CODE__XML_ELEMENT = "national-destination-code";

	/** XML attribute or element name of a {@link #getPrefix} property. */
	private static final String PREFIX__XML_ATTR = "prefix";

	/** XML attribute or element name of a {@link #getMaxDigits} property. */
	private static final String MAX_DIGITS__XML_ATTR = "max-digits";

	/** XML attribute or element name of a {@link #getMinDigits} property. */
	private static final String MIN_DIGITS__XML_ATTR = "min-digits";

	/** XML attribute or element name of a {@link #getUsage} property. */
	private static final String USAGE__XML_ATTR = "usage";

	/** XML attribute or element name of a {@link #getInfo} property. */
	private static final String INFO__XML_ATTR = "info";

	@Override
	public String getXmlTagName() {
		return NATIONAL_DESTINATION_CODE__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(PREFIX__XML_ATTR, getPrefix());
		out.writeAttribute(MAX_DIGITS__XML_ATTR, Integer.toString(getMaxDigits()));
		out.writeAttribute(MIN_DIGITS__XML_ATTR, Integer.toString(getMinDigits()));
		out.writeAttribute(USAGE__XML_ATTR, getUsage());
		out.writeAttribute(INFO__XML_ATTR, getInfo());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.analysis.model.NationalDestinationCode} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static NationalDestinationCode readNationalDestinationCode_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		NationalDestinationCode result = new NationalDestinationCode();
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
			case PREFIX__XML_ATTR: {
				setPrefix(value);
				break;
			}
			case MAX_DIGITS__XML_ATTR: {
				setMaxDigits(Integer.parseInt(value));
				break;
			}
			case MIN_DIGITS__XML_ATTR: {
				setMinDigits(Integer.parseInt(value));
				break;
			}
			case USAGE__XML_ATTR: {
				setUsage(value);
				break;
			}
			case INFO__XML_ATTR: {
				setInfo(value);
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
			case PREFIX__XML_ATTR: {
				setPrefix(in.getElementText());
				break;
			}
			case MAX_DIGITS__XML_ATTR: {
				setMaxDigits(Integer.parseInt(in.getElementText()));
				break;
			}
			case MIN_DIGITS__XML_ATTR: {
				setMinDigits(Integer.parseInt(in.getElementText()));
				break;
			}
			case USAGE__XML_ATTR: {
				setUsage(in.getElementText());
				break;
			}
			case INFO__XML_ATTR: {
				setInfo(in.getElementText());
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

	/** Creates a new {@link NationalDestinationCode} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static NationalDestinationCode readNationalDestinationCode(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.analysis.model.NationalDestinationCode.readNationalDestinationCode_XmlContent(in);
	}

}
