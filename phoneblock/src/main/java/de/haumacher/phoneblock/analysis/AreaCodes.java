package de.haumacher.phoneblock.analysis;

public class AreaCodes extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.analysis.AreaCodes} instance.
	 */
	public static de.haumacher.phoneblock.analysis.AreaCodes create() {
		return new de.haumacher.phoneblock.analysis.AreaCodes();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.analysis.AreaCodes} type in JSON format. */
	public static final String AREA_CODES__TYPE = "AreaCodes";

	/** @see #getCodes() */
	private static final String CODES__PROP = "codes";

	private final java.util.Map<String, de.haumacher.phoneblock.analysis.AreaCode> _codes = new java.util.LinkedHashMap<>();

	/**
	 * Creates a {@link AreaCodes} instance.
	 *
	 * @see de.haumacher.phoneblock.analysis.AreaCodes#create()
	 */
	protected AreaCodes() {
		super();
	}

	public final java.util.Map<String, de.haumacher.phoneblock.analysis.AreaCode> getCodes() {
		return _codes;
	}

	/**
	 * @see #getCodes()
	 */
	public de.haumacher.phoneblock.analysis.AreaCodes setCodes(java.util.Map<String, de.haumacher.phoneblock.analysis.AreaCode> value) {
		internalSetCodes(value);
		return this;
	}

	/** Internal setter for {@link #getCodes()} without chain call utility. */
	protected final void internalSetCodes(java.util.Map<String, de.haumacher.phoneblock.analysis.AreaCode> value) {
		if (value == null) throw new IllegalArgumentException("Property 'codes' cannot be null.");
		_codes.clear();
		_codes.putAll(value);
	}

	/**
	 * Adds a key value pair to the {@link #getCodes()} map.
	 */
	public de.haumacher.phoneblock.analysis.AreaCodes putCode(String key, de.haumacher.phoneblock.analysis.AreaCode value) {
		internalPutCode(key, value);
		return this;
	}

	/** Implementation of {@link #putCode(String, de.haumacher.phoneblock.analysis.AreaCode)} without chain call utility. */
	protected final void  internalPutCode(String key, de.haumacher.phoneblock.analysis.AreaCode value) {
		if (_codes.containsKey(key)) {
			throw new IllegalArgumentException("Property 'codes' already contains a value for key '" + key + "'.");
		}
		_codes.put(key, value);
	}

	/**
	 * Removes a key from the {@link #getCodes()} map.
	 */
	public final void removeCode(String key) {
		_codes.remove(key);
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.analysis.AreaCodes readAreaCodes(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.analysis.AreaCodes result = new de.haumacher.phoneblock.analysis.AreaCodes();
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
		out.name(CODES__PROP);
		out.beginObject();
		for (java.util.Map.Entry<String,de.haumacher.phoneblock.analysis.AreaCode> entry : getCodes().entrySet()) {
			out.name(entry.getKey());
			entry.getValue().writeTo(out);
		}
		out.endObject();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case CODES__PROP: {
				java.util.Map<String, de.haumacher.phoneblock.analysis.AreaCode> newValue = new java.util.LinkedHashMap<>();
				in.beginObject();
				while (in.hasNext()) {
					newValue.put(in.nextName(), de.haumacher.phoneblock.analysis.AreaCode.readAreaCode(in));
				}
				in.endObject();
				setCodes(newValue);
				break;
			}
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.analysis.AreaCodes} type. */
	public static final String AREA_CODES__XML_ELEMENT = "area-codes";

	/** XML attribute or element name of a {@link #getCodes} property. */
	private static final String CODES__XML_ATTR = "codes";

	@Override
	public String getXmlTagName() {
		return AREA_CODES__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.analysis.AreaCodes} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AreaCodes readAreaCodes_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		AreaCodes result = new AreaCodes();
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
			default: {
				// Skip unknown attribute.
			}
		}
	}

	/** Reads the element under the cursor and assigns its contents to the field with the given name. */
	protected void readFieldXmlElement(javax.xml.stream.XMLStreamReader in, String localName) throws javax.xml.stream.XMLStreamException {
		switch (localName) {
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

	/** Creates a new {@link AreaCodes} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AreaCodes readAreaCodes(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.analysis.AreaCodes.readAreaCodes_XmlContent(in);
	}

}
