package de.haumacher.phoneblock.db.model;

public class NumberInfo extends AbstractNumberInfo {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.model.NumberInfo} instance.
	 */
	public static de.haumacher.phoneblock.db.model.NumberInfo create() {
		return new de.haumacher.phoneblock.db.model.NumberInfo();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.model.NumberInfo} type in JSON format. */
	public static final String NUMBER_INFO__TYPE = "NumberInfo";

	/** @see #getAdded() */
	public static final String ADDED__PROP = "added";

	/** @see #getUpdated() */
	public static final String UPDATED__PROP = "updated";

	private long _added = 0L;

	private long _updated = 0L;

	/**
	 * Creates a {@link NumberInfo} instance.
	 *
	 * @see de.haumacher.phoneblock.db.model.NumberInfo#create()
	 */
	protected NumberInfo() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.NUMBER_INFO;
	}

	/**
	 * Time when the number was inserted
	 */
	public final long getAdded() {
		return _added;
	}

	/**
	 * @see #getAdded()
	 */
	public de.haumacher.phoneblock.db.model.NumberInfo setAdded(long value) {
		internalSetAdded(value);
		return this;
	}

	/** Internal setter for {@link #getAdded()} without chain call utility. */
	protected final void internalSetAdded(long value) {
		_listener.beforeSet(this, ADDED__PROP, value);
		_added = value;
	}

	/**
	 * Time when the information was last updated.
	 */
	public final long getUpdated() {
		return _updated;
	}

	/**
	 * @see #getUpdated()
	 */
	public de.haumacher.phoneblock.db.model.NumberInfo setUpdated(long value) {
		internalSetUpdated(value);
		return this;
	}

	/** Internal setter for {@link #getUpdated()} without chain call utility. */
	protected final void internalSetUpdated(long value) {
		_listener.beforeSet(this, UPDATED__PROP, value);
		_updated = value;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setActive(boolean value) {
		internalSetActive(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setCalls(int value) {
		internalSetCalls(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setVotes(int value) {
		internalSetVotes(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setRatingLegitimate(int value) {
		internalSetRatingLegitimate(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setRatingPing(int value) {
		internalSetRatingPing(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setRatingPoll(int value) {
		internalSetRatingPoll(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setRatingAdvertising(int value) {
		internalSetRatingAdvertising(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setRatingGamble(int value) {
		internalSetRatingGamble(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setRatingFraud(int value) {
		internalSetRatingFraud(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberInfo setSearches(int value) {
		internalSetSearches(value);
		return this;
	}

	@Override
	public String jsonType() {
		return NUMBER_INFO__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ADDED__PROP, 
			UPDATED__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ADDED__PROP: return getAdded();
			case UPDATED__PROP: return getUpdated();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ADDED__PROP: internalSetAdded((long) value); break;
			case UPDATED__PROP: internalSetUpdated((long) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.model.NumberInfo readNumberInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.NumberInfo result = new de.haumacher.phoneblock.db.model.NumberInfo();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(ADDED__PROP);
		out.value(getAdded());
		out.name(UPDATED__PROP);
		out.value(getUpdated());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ADDED__PROP: setAdded(in.nextLong()); break;
			case UPDATED__PROP: setUpdated(in.nextLong()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.db.model.NumberInfo} type. */
	public static final String NUMBER_INFO__XML_ELEMENT = "number-info";

	/** XML attribute or element name of a {@link #getAdded} property. */
	private static final String ADDED__XML_ATTR = "added";

	/** XML attribute or element name of a {@link #getUpdated} property. */
	private static final String UPDATED__XML_ATTR = "updated";

	@Override
	public String getXmlTagName() {
		return NUMBER_INFO__XML_ELEMENT;
	}

	/** Serializes all fields that are written as XML attributes. */
	@Override
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeAttributes(out);
		out.writeAttribute(ADDED__XML_ATTR, Long.toString(getAdded()));
		out.writeAttribute(UPDATED__XML_ATTR, Long.toString(getUpdated()));
	}

	/** Serializes all fields that are written as XML elements. */
	@Override
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeElements(out);
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.db.model.NumberInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static NumberInfo readNumberInfo_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		NumberInfo result = new NumberInfo();
		result.readContentXml(in);
		return result;
	}

	@Override
	protected void readFieldXmlAttribute(String name, String value) {
		switch (name) {
			case ADDED__XML_ATTR: {
				setAdded(Long.parseLong(value));
				break;
			}
			case UPDATED__XML_ATTR: {
				setUpdated(Long.parseLong(value));
				break;
			}
			default: {
				super.readFieldXmlAttribute(name, value);
			}
		}
	}

	@Override
	protected void readFieldXmlElement(javax.xml.stream.XMLStreamReader in, String localName) throws javax.xml.stream.XMLStreamException {
		switch (localName) {
			case ADDED__XML_ATTR: {
				setAdded(Long.parseLong(in.getElementText()));
				break;
			}
			case UPDATED__XML_ATTR: {
				setUpdated(Long.parseLong(in.getElementText()));
				break;
			}
			default: {
				super.readFieldXmlElement(in, localName);
			}
		}
	}

	/** Creates a new {@link NumberInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static NumberInfo readNumberInfo(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.db.model.NumberInfo.readNumberInfo_XmlContent(in);
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.db.model.AbstractNumberInfo.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
