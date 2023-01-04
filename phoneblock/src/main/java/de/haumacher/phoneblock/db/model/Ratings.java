package de.haumacher.phoneblock.db.model;

public class Ratings extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.model.Ratings} instance.
	 */
	public static de.haumacher.phoneblock.db.model.Ratings create() {
		return new de.haumacher.phoneblock.db.model.Ratings();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.model.Ratings} type in JSON format. */
	public static final String RATINGS__TYPE = "Ratings";

	/** @see #getValues() */
	public static final String VALUES__PROP = "values";

	private final java.util.List<de.haumacher.phoneblock.db.model.Rating> _values = new de.haumacher.msgbuf.util.ReferenceList<de.haumacher.phoneblock.db.model.Rating>() {
		@Override
		protected void beforeAdd(int index, de.haumacher.phoneblock.db.model.Rating element) {
			_listener.beforeAdd(Ratings.this, VALUES__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, de.haumacher.phoneblock.db.model.Rating element) {
			_listener.afterRemove(Ratings.this, VALUES__PROP, index, element);
		}
	};

	/**
	 * Creates a {@link Ratings} instance.
	 *
	 * @see de.haumacher.phoneblock.db.model.Ratings#create()
	 */
	protected Ratings() {
		super();
	}

	public final java.util.List<de.haumacher.phoneblock.db.model.Rating> getValues() {
		return _values;
	}

	/**
	 * @see #getValues()
	 */
	public de.haumacher.phoneblock.db.model.Ratings setValues(java.util.List<? extends de.haumacher.phoneblock.db.model.Rating> value) {
		internalSetValues(value);
		return this;
	}

	/** Internal setter for {@link #getValues()} without chain call utility. */
	protected final void internalSetValues(java.util.List<? extends de.haumacher.phoneblock.db.model.Rating> value) {
		if (value == null) throw new IllegalArgumentException("Property 'values' cannot be null.");
		_values.clear();
		_values.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getValues()} list.
	 */
	public de.haumacher.phoneblock.db.model.Ratings addValue(de.haumacher.phoneblock.db.model.Rating value) {
		internalAddValue(value);
		return this;
	}

	/** Implementation of {@link #addValue(de.haumacher.phoneblock.db.model.Rating)} without chain call utility. */
	protected final void internalAddValue(de.haumacher.phoneblock.db.model.Rating value) {
		_values.add(value);
	}

	/**
	 * Removes a value from the {@link #getValues()} list.
	 */
	public final void removeValue(de.haumacher.phoneblock.db.model.Rating value) {
		_values.remove(value);
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.model.Ratings registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.model.Ratings unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return RATINGS__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			VALUES__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case VALUES__PROP: return getValues();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case VALUES__PROP: internalSetValues(de.haumacher.msgbuf.util.Conversions.asList(de.haumacher.phoneblock.db.model.Rating.class, value)); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.model.Ratings readRatings(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.Ratings result = new de.haumacher.phoneblock.db.model.Ratings();
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
		out.name(VALUES__PROP);
		out.beginArray();
		for (de.haumacher.phoneblock.db.model.Rating x : getValues()) {
			x.writeTo(out);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case VALUES__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addValue(de.haumacher.phoneblock.db.model.Rating.readRating(in));
				}
				in.endArray();
			}
			break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.db.model.Ratings} type. */
	public static final String RATINGS__XML_ELEMENT = "ratings";

	/** XML attribute or element name of a {@link #getValues} property. */
	private static final String VALUES__XML_ATTR = "values";

	@Override
	public String getXmlTagName() {
		return RATINGS__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(VALUES__XML_ATTR, getValues().stream().map(x -> x.protocolName()).collect(java.util.stream.Collectors.joining(", ")));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.db.model.Ratings} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static Ratings readRatings_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		Ratings result = new Ratings();
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
			case VALUES__XML_ATTR: {
				setValues(java.util.Arrays.stream(value.split("\\s*,\\s*")).map(x -> de.haumacher.phoneblock.db.model.Rating.valueOfProtocol(x)).collect(java.util.stream.Collectors.toList()));
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
			case VALUES__XML_ATTR: {
				setValues(java.util.Arrays.stream(in.getElementText().split("\\s*,\\s*")).map(x -> de.haumacher.phoneblock.db.model.Rating.valueOfProtocol(x)).collect(java.util.stream.Collectors.toList()));
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

	/** Creates a new {@link Ratings} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static Ratings readRatings(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.db.model.Ratings.readRatings_XmlContent(in);
	}

}
