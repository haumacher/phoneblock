package de.haumacher.phoneblock.app.api.model;

/**
 * A single aggregated SPAM range matching a hash prefix in a {@link PrefixCheckResult}.
 */
public class RangeMatch extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.RangeMatch} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.RangeMatch create() {
		return new de.haumacher.phoneblock.app.api.model.RangeMatch();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.RangeMatch} type in JSON format. */
	public static final String RANGE_MATCH__TYPE = "RangeMatch";

	/** @see #getPrefix() */
	private static final String PREFIX__PROP = "prefix";

	/** @see #getVotes() */
	private static final String VOTES__PROP = "votes";

	/** @see #getCnt() */
	private static final String CNT__PROP = "cnt";

	private String _prefix = "";

	private int _votes = 0;

	private int _cnt = 0;

	/**
	 * Creates a {@link RangeMatch} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.RangeMatch#create()
	 */
	protected RangeMatch() {
		super();
	}

	/**
	 * The plaintext prefix of the aggregated range (e.g. "+4930123").
	 */
	public final String getPrefix() {
		return _prefix;
	}

	/**
	 * @see #getPrefix()
	 */
	public de.haumacher.phoneblock.app.api.model.RangeMatch setPrefix(String value) {
		internalSetPrefix(value);
		return this;
	}

	/** Internal setter for {@link #getPrefix()} without chain call utility. */
	protected final void internalSetPrefix(String value) {
		_prefix = value;
	}

	/**
	 * Aggregated number of SPAM votes for all numbers in this range.
	 */
	public final int getVotes() {
		return _votes;
	}

	/**
	 * @see #getVotes()
	 */
	public de.haumacher.phoneblock.app.api.model.RangeMatch setVotes(int value) {
		internalSetVotes(value);
		return this;
	}

	/** Internal setter for {@link #getVotes()} without chain call utility. */
	protected final void internalSetVotes(int value) {
		_votes = value;
	}

	/**
	 * Number of distinct SPAM numbers known for this range.
	 */
	public final int getCnt() {
		return _cnt;
	}

	/**
	 * @see #getCnt()
	 */
	public de.haumacher.phoneblock.app.api.model.RangeMatch setCnt(int value) {
		internalSetCnt(value);
		return this;
	}

	/** Internal setter for {@link #getCnt()} without chain call utility. */
	protected final void internalSetCnt(int value) {
		_cnt = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.RangeMatch readRangeMatch(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RangeMatch result = new de.haumacher.phoneblock.app.api.model.RangeMatch();
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
		out.name(VOTES__PROP);
		out.value(getVotes());
		out.name(CNT__PROP);
		out.value(getCnt());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PREFIX__PROP: setPrefix(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case VOTES__PROP: setVotes(in.nextInt()); break;
			case CNT__PROP: setCnt(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.RangeMatch} type. */
	public static final String RANGE_MATCH__XML_ELEMENT = "range-match";

	/** XML attribute or element name of a {@link #getPrefix} property. */
	private static final String PREFIX__XML_ATTR = "prefix";

	/** XML attribute or element name of a {@link #getVotes} property. */
	private static final String VOTES__XML_ATTR = "votes";

	/** XML attribute or element name of a {@link #getCnt} property. */
	private static final String CNT__XML_ATTR = "cnt";

	@Override
	public String getXmlTagName() {
		return RANGE_MATCH__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(PREFIX__XML_ATTR, getPrefix());
		out.writeAttribute(VOTES__XML_ATTR, Integer.toString(getVotes()));
		out.writeAttribute(CNT__XML_ATTR, Integer.toString(getCnt()));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.RangeMatch} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RangeMatch readRangeMatch_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		RangeMatch result = new RangeMatch();
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
			case VOTES__XML_ATTR: {
				setVotes(Integer.parseInt(value));
				break;
			}
			case CNT__XML_ATTR: {
				setCnt(Integer.parseInt(value));
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
			case VOTES__XML_ATTR: {
				setVotes(Integer.parseInt(in.getElementText()));
				break;
			}
			case CNT__XML_ATTR: {
				setCnt(Integer.parseInt(in.getElementText()));
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

	/** Creates a new {@link RangeMatch} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RangeMatch readRangeMatch(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.RangeMatch.readRangeMatch_XmlContent(in);
	}

}
