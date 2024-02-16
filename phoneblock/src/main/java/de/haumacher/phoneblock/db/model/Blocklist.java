package de.haumacher.phoneblock.db.model;

/**
 * List of blocked numbers for retrieval through the <i>PhoneBlock API</i>.
 */
public class Blocklist extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.model.Blocklist} instance.
	 */
	public static de.haumacher.phoneblock.db.model.Blocklist create() {
		return new de.haumacher.phoneblock.db.model.Blocklist();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.model.Blocklist} type in JSON format. */
	public static final String BLOCKLIST__TYPE = "Blocklist";

	/** @see #getNumbers() */
	public static final String NUMBERS__PROP = "numbers";

	private final java.util.List<de.haumacher.phoneblock.db.model.PhoneInfo> _numbers = new de.haumacher.msgbuf.util.ReferenceList<>() {
		@Override
		protected void beforeAdd(int index, de.haumacher.phoneblock.db.model.PhoneInfo element) {
			_listener.beforeAdd(Blocklist.this, NUMBERS__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, de.haumacher.phoneblock.db.model.PhoneInfo element) {
			_listener.afterRemove(Blocklist.this, NUMBERS__PROP, index, element);
		}
	};

	/**
	 * Creates a {@link Blocklist} instance.
	 *
	 * @see de.haumacher.phoneblock.db.model.Blocklist#create()
	 */
	protected Blocklist() {
		super();
	}

	/**
	 * Numbers in the blocklist.
	 */
	public final java.util.List<de.haumacher.phoneblock.db.model.PhoneInfo> getNumbers() {
		return _numbers;
	}

	/**
	 * @see #getNumbers()
	 */
	public de.haumacher.phoneblock.db.model.Blocklist setNumbers(java.util.List<? extends de.haumacher.phoneblock.db.model.PhoneInfo> value) {
		internalSetNumbers(value);
		return this;
	}

	/** Internal setter for {@link #getNumbers()} without chain call utility. */
	protected final void internalSetNumbers(java.util.List<? extends de.haumacher.phoneblock.db.model.PhoneInfo> value) {
		if (value == null) throw new IllegalArgumentException("Property 'numbers' cannot be null.");
		_numbers.clear();
		_numbers.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getNumbers()} list.
	 */
	public de.haumacher.phoneblock.db.model.Blocklist addNumber(de.haumacher.phoneblock.db.model.PhoneInfo value) {
		internalAddNumber(value);
		return this;
	}

	/** Implementation of {@link #addNumber(de.haumacher.phoneblock.db.model.PhoneInfo)} without chain call utility. */
	protected final void internalAddNumber(de.haumacher.phoneblock.db.model.PhoneInfo value) {
		_numbers.add(value);
	}

	/**
	 * Removes a value from the {@link #getNumbers()} list.
	 */
	public final void removeNumber(de.haumacher.phoneblock.db.model.PhoneInfo value) {
		_numbers.remove(value);
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.model.Blocklist registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.model.Blocklist unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return BLOCKLIST__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			NUMBERS__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case NUMBERS__PROP: return getNumbers();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case NUMBERS__PROP: internalSetNumbers(de.haumacher.msgbuf.util.Conversions.asList(de.haumacher.phoneblock.db.model.PhoneInfo.class, value)); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.model.Blocklist readBlocklist(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.Blocklist result = new de.haumacher.phoneblock.db.model.Blocklist();
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
		for (de.haumacher.phoneblock.db.model.PhoneInfo x : getNumbers()) {
			x.writeTo(out);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case NUMBERS__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addNumber(de.haumacher.phoneblock.db.model.PhoneInfo.readPhoneInfo(in));
				}
				in.endArray();
			}
			break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.db.model.Blocklist} type. */
	public static final String BLOCKLIST__XML_ELEMENT = "blocklist";

	/** XML attribute or element name of a {@link #getNumbers} property. */
	private static final String NUMBERS__XML_ATTR = "numbers";

	@Override
	public String getXmlTagName() {
		return BLOCKLIST__XML_ELEMENT;
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
		for (de.haumacher.phoneblock.db.model.PhoneInfo element : getNumbers()) {
			element.writeTo(out);
		}
		out.writeEndElement();
	}

	/** Creates a new {@link de.haumacher.phoneblock.db.model.Blocklist} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static Blocklist readBlocklist_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		Blocklist result = new Blocklist();
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

			addNumber(de.haumacher.phoneblock.db.model.PhoneInfo.readPhoneInfo_XmlContent(in));
		}
	}

	/** Creates a new {@link Blocklist} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static Blocklist readBlocklist(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.db.model.Blocklist.readBlocklist_XmlContent(in);
	}

}
