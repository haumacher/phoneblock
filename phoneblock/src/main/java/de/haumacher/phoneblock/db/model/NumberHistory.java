package de.haumacher.phoneblock.db.model;

public class NumberHistory extends AbstractNumberInfo {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.model.NumberHistory} instance.
	 */
	public static de.haumacher.phoneblock.db.model.NumberHistory create() {
		return new de.haumacher.phoneblock.db.model.NumberHistory();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.model.NumberHistory} type in JSON format. */
	public static final String NUMBER_HISTORY__TYPE = "NumberHistory";

	/** @see #getRMin() */
	public static final String R_MIN__PROP = "rMin";

	/** @see #getRMax() */
	public static final String R_MAX__PROP = "rMax";

	private int _rMin = 0;

	private int _rMax = 0;

	/**
	 * Creates a {@link NumberHistory} instance.
	 *
	 * @see de.haumacher.phoneblock.db.model.NumberHistory#create()
	 */
	protected NumberHistory() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.NUMBER_HISTORY;
	}

	/**
	 * The revision in which this information was stored.
	 */
	public final int getRMin() {
		return _rMin;
	}

	/**
	 * @see #getRMin()
	 */
	public de.haumacher.phoneblock.db.model.NumberHistory setRMin(int value) {
		internalSetRMin(value);
		return this;
	}

	/** Internal setter for {@link #getRMin()} without chain call utility. */
	protected final void internalSetRMin(int value) {
		_listener.beforeSet(this, R_MIN__PROP, value);
		_rMin = value;
	}

	/**
	 * The revision up to which this information is valid (inclusive).
	 */
	public final int getRMax() {
		return _rMax;
	}

	/**
	 * @see #getRMax()
	 */
	public de.haumacher.phoneblock.db.model.NumberHistory setRMax(int value) {
		internalSetRMax(value);
		return this;
	}

	/** Internal setter for {@link #getRMax()} without chain call utility. */
	protected final void internalSetRMax(int value) {
		_listener.beforeSet(this, R_MAX__PROP, value);
		_rMax = value;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setActive(boolean value) {
		internalSetActive(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setCalls(int value) {
		internalSetCalls(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setVotes(int value) {
		internalSetVotes(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setRatingLegitimate(int value) {
		internalSetRatingLegitimate(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setRatingPing(int value) {
		internalSetRatingPing(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setRatingPoll(int value) {
		internalSetRatingPoll(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setRatingAdvertising(int value) {
		internalSetRatingAdvertising(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setRatingGamble(int value) {
		internalSetRatingGamble(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setRatingFraud(int value) {
		internalSetRatingFraud(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.NumberHistory setSearches(int value) {
		internalSetSearches(value);
		return this;
	}

	@Override
	public String jsonType() {
		return NUMBER_HISTORY__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			R_MIN__PROP, 
			R_MAX__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case R_MIN__PROP: return getRMin();
			case R_MAX__PROP: return getRMax();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case R_MIN__PROP: internalSetRMin((int) value); break;
			case R_MAX__PROP: internalSetRMax((int) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.model.NumberHistory readNumberHistory(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.NumberHistory result = new de.haumacher.phoneblock.db.model.NumberHistory();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(R_MIN__PROP);
		out.value(getRMin());
		out.name(R_MAX__PROP);
		out.value(getRMax());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case R_MIN__PROP: setRMin(in.nextInt()); break;
			case R_MAX__PROP: setRMax(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.db.model.NumberHistory} type. */
	public static final String NUMBER_HISTORY__XML_ELEMENT = "number-history";

	/** XML attribute or element name of a {@link #getRMin} property. */
	private static final String R_MIN__XML_ATTR = "r-min";

	/** XML attribute or element name of a {@link #getRMax} property. */
	private static final String R_MAX__XML_ATTR = "r-max";

	@Override
	public String getXmlTagName() {
		return NUMBER_HISTORY__XML_ELEMENT;
	}

	/** Serializes all fields that are written as XML attributes. */
	@Override
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeAttributes(out);
		out.writeAttribute(R_MIN__XML_ATTR, Integer.toString(getRMin()));
		out.writeAttribute(R_MAX__XML_ATTR, Integer.toString(getRMax()));
	}

	/** Serializes all fields that are written as XML elements. */
	@Override
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeElements(out);
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.db.model.NumberHistory} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static NumberHistory readNumberHistory_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		NumberHistory result = new NumberHistory();
		result.readContentXml(in);
		return result;
	}

	@Override
	protected void readFieldXmlAttribute(String name, String value) {
		switch (name) {
			case R_MIN__XML_ATTR: {
				setRMin(Integer.parseInt(value));
				break;
			}
			case R_MAX__XML_ATTR: {
				setRMax(Integer.parseInt(value));
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
			case R_MIN__XML_ATTR: {
				setRMin(Integer.parseInt(in.getElementText()));
				break;
			}
			case R_MAX__XML_ATTR: {
				setRMax(Integer.parseInt(in.getElementText()));
				break;
			}
			default: {
				super.readFieldXmlElement(in, localName);
			}
		}
	}

	/** Creates a new {@link NumberHistory} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static NumberHistory readNumberHistory(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.db.model.NumberHistory.readNumberHistory_XmlContent(in);
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.db.model.AbstractNumberInfo.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
