package de.haumacher.phoneblock.app.api.model;

/**
 * Response of the privacy-preserving k-anonymity prefix lookup
 * (see {@link de.haumacher.phoneblock.app.api.PrefixCheckServlet}).
 *
 * <p>
 * The server returns all SPAM numbers / range aggregations whose SHA-1 hashes
 * start with the provided hex prefixes. The client matches the entries
 * against its own (locally-hashed) number to decide whether the caller is on
 * the blocklist — the server never sees the plaintext number.
 * </p>
 */
public class PrefixCheckResult extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.PrefixCheckResult} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.PrefixCheckResult create() {
		return new de.haumacher.phoneblock.app.api.model.PrefixCheckResult();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.PrefixCheckResult} type in JSON format. */
	public static final String PREFIX_CHECK_RESULT__TYPE = "PrefixCheckResult";

	/** @see #getNumbers() */
	private static final String NUMBERS__PROP = "numbers";

	/** @see #getRange10() */
	private static final String RANGE_10__PROP = "range10";

	/** @see #getRange100() */
	private static final String RANGE_100__PROP = "range100";

	private final java.util.List<de.haumacher.phoneblock.app.api.model.PhoneInfo> _numbers = new java.util.ArrayList<>();

	private final java.util.List<de.haumacher.phoneblock.app.api.model.RangeMatch> _range10 = new java.util.ArrayList<>();

	private final java.util.List<de.haumacher.phoneblock.app.api.model.RangeMatch> _range100 = new java.util.ArrayList<>();

	/**
	 * Creates a {@link PrefixCheckResult} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.PrefixCheckResult#create()
	 */
	protected PrefixCheckResult() {
		super();
	}

	/**
	 * Phone numbers whose SHA-1 hash starts with the {@code sha1} prefix from the request.
	 */
	public final java.util.List<de.haumacher.phoneblock.app.api.model.PhoneInfo> getNumbers() {
		return _numbers;
	}

	/**
	 * @see #getNumbers()
	 */
	public de.haumacher.phoneblock.app.api.model.PrefixCheckResult setNumbers(java.util.List<? extends de.haumacher.phoneblock.app.api.model.PhoneInfo> value) {
		internalSetNumbers(value);
		return this;
	}

	/** Internal setter for {@link #getNumbers()} without chain call utility. */
	protected final void internalSetNumbers(java.util.List<? extends de.haumacher.phoneblock.app.api.model.PhoneInfo> value) {
		if (value == null) throw new IllegalArgumentException("Property 'numbers' cannot be null.");
		_numbers.clear();
		_numbers.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getNumbers()} list.
	 */
	public de.haumacher.phoneblock.app.api.model.PrefixCheckResult addNumber(de.haumacher.phoneblock.app.api.model.PhoneInfo value) {
		internalAddNumber(value);
		return this;
	}

	/** Implementation of {@link #addNumber(de.haumacher.phoneblock.app.api.model.PhoneInfo)} without chain call utility. */
	protected final void internalAddNumber(de.haumacher.phoneblock.app.api.model.PhoneInfo value) {
		_numbers.add(value);
	}

	/**
	 * Removes a value from the {@link #getNumbers()} list.
	 */
	public final void removeNumber(de.haumacher.phoneblock.app.api.model.PhoneInfo value) {
		_numbers.remove(value);
	}

	/**
	 * 10-digit range aggregations whose SHA-1 hash starts with the {@code prefix10} prefix from the request. Empty if no {@code prefix10} was supplied or no match was found.
	 */
	public final java.util.List<de.haumacher.phoneblock.app.api.model.RangeMatch> getRange10() {
		return _range10;
	}

	/**
	 * @see #getRange10()
	 */
	public de.haumacher.phoneblock.app.api.model.PrefixCheckResult setRange10(java.util.List<? extends de.haumacher.phoneblock.app.api.model.RangeMatch> value) {
		internalSetRange10(value);
		return this;
	}

	/** Internal setter for {@link #getRange10()} without chain call utility. */
	protected final void internalSetRange10(java.util.List<? extends de.haumacher.phoneblock.app.api.model.RangeMatch> value) {
		if (value == null) throw new IllegalArgumentException("Property 'range10' cannot be null.");
		_range10.clear();
		_range10.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getRange10()} list.
	 */
	public de.haumacher.phoneblock.app.api.model.PrefixCheckResult addRange10(de.haumacher.phoneblock.app.api.model.RangeMatch value) {
		internalAddRange10(value);
		return this;
	}

	/** Implementation of {@link #addRange10(de.haumacher.phoneblock.app.api.model.RangeMatch)} without chain call utility. */
	protected final void internalAddRange10(de.haumacher.phoneblock.app.api.model.RangeMatch value) {
		_range10.add(value);
	}

	/**
	 * Removes a value from the {@link #getRange10()} list.
	 */
	public final void removeRange10(de.haumacher.phoneblock.app.api.model.RangeMatch value) {
		_range10.remove(value);
	}

	/**
	 * 100-digit range aggregations whose SHA-1 hash starts with the {@code prefix100} prefix from the request. Empty if no {@code prefix100} was supplied or no match was found.
	 */
	public final java.util.List<de.haumacher.phoneblock.app.api.model.RangeMatch> getRange100() {
		return _range100;
	}

	/**
	 * @see #getRange100()
	 */
	public de.haumacher.phoneblock.app.api.model.PrefixCheckResult setRange100(java.util.List<? extends de.haumacher.phoneblock.app.api.model.RangeMatch> value) {
		internalSetRange100(value);
		return this;
	}

	/** Internal setter for {@link #getRange100()} without chain call utility. */
	protected final void internalSetRange100(java.util.List<? extends de.haumacher.phoneblock.app.api.model.RangeMatch> value) {
		if (value == null) throw new IllegalArgumentException("Property 'range100' cannot be null.");
		_range100.clear();
		_range100.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getRange100()} list.
	 */
	public de.haumacher.phoneblock.app.api.model.PrefixCheckResult addRange100(de.haumacher.phoneblock.app.api.model.RangeMatch value) {
		internalAddRange100(value);
		return this;
	}

	/** Implementation of {@link #addRange100(de.haumacher.phoneblock.app.api.model.RangeMatch)} without chain call utility. */
	protected final void internalAddRange100(de.haumacher.phoneblock.app.api.model.RangeMatch value) {
		_range100.add(value);
	}

	/**
	 * Removes a value from the {@link #getRange100()} list.
	 */
	public final void removeRange100(de.haumacher.phoneblock.app.api.model.RangeMatch value) {
		_range100.remove(value);
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.PrefixCheckResult readPrefixCheckResult(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.PrefixCheckResult result = new de.haumacher.phoneblock.app.api.model.PrefixCheckResult();
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
		out.name(NUMBERS__PROP);
		out.beginArray();
		for (de.haumacher.phoneblock.app.api.model.PhoneInfo x : getNumbers()) {
			x.writeTo(out);
		}
		out.endArray();
		out.name(RANGE_10__PROP);
		out.beginArray();
		for (de.haumacher.phoneblock.app.api.model.RangeMatch x : getRange10()) {
			x.writeTo(out);
		}
		out.endArray();
		out.name(RANGE_100__PROP);
		out.beginArray();
		for (de.haumacher.phoneblock.app.api.model.RangeMatch x : getRange100()) {
			x.writeTo(out);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case NUMBERS__PROP: {
				java.util.List<de.haumacher.phoneblock.app.api.model.PhoneInfo> newValue = new java.util.ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					newValue.add(de.haumacher.phoneblock.app.api.model.PhoneInfo.readPhoneInfo(in));
				}
				in.endArray();
				setNumbers(newValue);
			}
			break;
			case RANGE_10__PROP: {
				java.util.List<de.haumacher.phoneblock.app.api.model.RangeMatch> newValue = new java.util.ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					newValue.add(de.haumacher.phoneblock.app.api.model.RangeMatch.readRangeMatch(in));
				}
				in.endArray();
				setRange10(newValue);
			}
			break;
			case RANGE_100__PROP: {
				java.util.List<de.haumacher.phoneblock.app.api.model.RangeMatch> newValue = new java.util.ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					newValue.add(de.haumacher.phoneblock.app.api.model.RangeMatch.readRangeMatch(in));
				}
				in.endArray();
				setRange100(newValue);
			}
			break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.PrefixCheckResult} type. */
	public static final String PREFIX_CHECK_RESULT__XML_ELEMENT = "prefix-check-result";

	/** XML attribute or element name of a {@link #getNumbers} property. */
	private static final String NUMBERS__XML_ATTR = "numbers";

	/** XML attribute or element name of a {@link #getRange10} property. */
	private static final String RANGE_10__XML_ATTR = "range-10";

	/** XML attribute or element name of a {@link #getRange100} property. */
	private static final String RANGE_100__XML_ATTR = "range-100";

	@Override
	public String getXmlTagName() {
		return PREFIX_CHECK_RESULT__XML_ELEMENT;
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
		out.writeStartElement(NUMBERS__XML_ATTR);
		for (de.haumacher.phoneblock.app.api.model.PhoneInfo element : getNumbers()) {
			element.writeTo(out);
		}
		out.writeEndElement();
		out.writeStartElement(RANGE_10__XML_ATTR);
		for (de.haumacher.phoneblock.app.api.model.RangeMatch element : getRange10()) {
			element.writeTo(out);
		}
		out.writeEndElement();
		out.writeStartElement(RANGE_100__XML_ATTR);
		for (de.haumacher.phoneblock.app.api.model.RangeMatch element : getRange100()) {
			element.writeTo(out);
		}
		out.writeEndElement();
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.PrefixCheckResult} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static PrefixCheckResult readPrefixCheckResult_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		PrefixCheckResult result = new PrefixCheckResult();
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
			case NUMBERS__XML_ATTR: {
				internalReadNumbersListXml(in);
				break;
			}
			case RANGE_10__XML_ATTR: {
				internalReadRange10ListXml(in);
				break;
			}
			case RANGE_100__XML_ATTR: {
				internalReadRange100ListXml(in);
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

	private void internalReadNumbersListXml(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		while (true) {
			int event = in.nextTag();
			if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT) {
				break;
			}

			addNumber(de.haumacher.phoneblock.app.api.model.PhoneInfo.readPhoneInfo_XmlContent(in));
		}
	}

	private void internalReadRange10ListXml(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		while (true) {
			int event = in.nextTag();
			if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT) {
				break;
			}

			addRange10(de.haumacher.phoneblock.app.api.model.RangeMatch.readRangeMatch_XmlContent(in));
		}
	}

	private void internalReadRange100ListXml(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		while (true) {
			int event = in.nextTag();
			if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT) {
				break;
			}

			addRange100(de.haumacher.phoneblock.app.api.model.RangeMatch.readRangeMatch_XmlContent(in));
		}
	}

	/** Creates a new {@link PrefixCheckResult} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static PrefixCheckResult readPrefixCheckResult(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.PrefixCheckResult.readPrefixCheckResult_XmlContent(in);
	}

}
