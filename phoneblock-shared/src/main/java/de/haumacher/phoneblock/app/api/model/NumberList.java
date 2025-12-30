package de.haumacher.phoneblock.app.api.model;

/**
 * A list of phone numbers (used for blacklist/whitelist responses).
 */
public class NumberList extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.NumberList} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.NumberList create() {
		return new de.haumacher.phoneblock.app.api.model.NumberList();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.NumberList} type in JSON format. */
	public static final String NUMBER_LIST__TYPE = "NumberList";

	/** @see #getNumbers() */
	public static final String NUMBERS__PROP = "numbers";

	private final java.util.List<String> _numbers = new de.haumacher.msgbuf.util.ReferenceList<>() {
		@Override
		protected void beforeAdd(int index, String element) {
			_listener.beforeAdd(NumberList.this, NUMBERS__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, String element) {
			_listener.afterRemove(NumberList.this, NUMBERS__PROP, index, element);
		}
	};

	/**
	 * Creates a {@link NumberList} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.NumberList#create()
	 */
	protected NumberList() {
		super();
	}

	/**
	 * Phone numbers in the list.
	 */
	public final java.util.List<String> getNumbers() {
		return _numbers;
	}

	/**
	 * @see #getNumbers()
	 */
	public de.haumacher.phoneblock.app.api.model.NumberList setNumbers(java.util.List<? extends String> value) {
		internalSetNumbers(value);
		return this;
	}

	/** Internal setter for {@link #getNumbers()} without chain call utility. */
	protected final void internalSetNumbers(java.util.List<? extends String> value) {
		_numbers.clear();
		_numbers.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getNumbers()} list.
	 */
	public de.haumacher.phoneblock.app.api.model.NumberList addNumber(String value) {
		internalAddNumber(value);
		return this;
	}

	/** Implementation of {@link #addNumber(String)} without chain call utility. */
	protected final void internalAddNumber(String value) {
		_numbers.add(value);
	}

	/**
	 * Removes a value from the {@link #getNumbers()} list.
	 */
	public final void removeNumber(String value) {
		_numbers.remove(value);
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.app.api.model.NumberList registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.NumberList unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return NUMBER_LIST__TYPE;
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
			case NUMBERS__PROP: internalSetNumbers(de.haumacher.msgbuf.util.Conversions.asList(String.class, value)); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.NumberList readNumberList(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.NumberList result = new de.haumacher.phoneblock.app.api.model.NumberList();
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
		for (String x : getNumbers()) {
			out.value(x);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case NUMBERS__PROP: {
				java.util.List<String> newValue = new java.util.ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					newValue.add(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
				setNumbers(newValue);
			}
			break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.NumberList} type. */
	public static final String NUMBER_LIST__XML_ELEMENT = "number-list";

	/** XML attribute or element name of a {@link #getNumbers} property. */
	private static final String NUMBERS__XML_ATTR = "numbers";

	@Override
	public String getXmlTagName() {
		return NUMBER_LIST__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(NUMBERS__XML_ATTR, getNumbers().stream().map(x -> x).collect(java.util.stream.Collectors.joining(", ")));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.NumberList} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static NumberList readNumberList_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		NumberList result = new NumberList();
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
			case NUMBERS__XML_ATTR: {
				setNumbers(java.util.Arrays.stream(value.split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
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
			case NUMBERS__XML_ATTR: {
				setNumbers(java.util.Arrays.stream(in.getElementText().split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
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

	/** Creates a new {@link NumberList} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static NumberList readNumberList(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.NumberList.readNumberList_XmlContent(in);
	}

}
