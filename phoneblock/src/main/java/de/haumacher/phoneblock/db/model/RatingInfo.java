package de.haumacher.phoneblock.db.model;

/**
 * Info of how often a certain number was rated in a certain way.
 */
public class RatingInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.model.RatingInfo} instance.
	 */
	public static de.haumacher.phoneblock.db.model.RatingInfo create() {
		return new de.haumacher.phoneblock.db.model.RatingInfo();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.model.RatingInfo} type in JSON format. */
	public static final String RATING_INFO__TYPE = "RatingInfo";

	/** @see #getPhone() */
	public static final String PHONE__PROP = "phone";

	/** @see #getRating() */
	public static final String RATING__PROP = "rating";

	/** @see #getVotes() */
	public static final String VOTES__PROP = "votes";

	private String _phone = "";

	private de.haumacher.phoneblock.db.model.Rating _rating = de.haumacher.phoneblock.db.model.Rating.A_LEGITIMATE;

	private int _votes = 0;

	/**
	 * Creates a {@link RatingInfo} instance.
	 *
	 * @see de.haumacher.phoneblock.db.model.RatingInfo#create()
	 */
	protected RatingInfo() {
		super();
	}

	/**
	 * The number being rated.
	 */
	public final String getPhone() {
		return _phone;
	}

	/**
	 * @see #getPhone()
	 */
	public de.haumacher.phoneblock.db.model.RatingInfo setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	/** Internal setter for {@link #getPhone()} without chain call utility. */
	protected final void internalSetPhone(String value) {
		_listener.beforeSet(this, PHONE__PROP, value);
		_phone = value;
	}

	/**
	 * The {@link Rating} of the {@link #getPhone() number}.
	 */
	public final de.haumacher.phoneblock.db.model.Rating getRating() {
		return _rating;
	}

	/**
	 * @see #getRating()
	 */
	public de.haumacher.phoneblock.db.model.RatingInfo setRating(de.haumacher.phoneblock.db.model.Rating value) {
		internalSetRating(value);
		return this;
	}

	/** Internal setter for {@link #getRating()} without chain call utility. */
	protected final void internalSetRating(de.haumacher.phoneblock.db.model.Rating value) {
		if (value == null) throw new IllegalArgumentException("Property 'rating' cannot be null.");
		_listener.beforeSet(this, RATING__PROP, value);
		_rating = value;
	}

	/**
	 * How often the {@link #getPhone() number} was rated in a {@link #getRating() certain way}.
	 */
	public final int getVotes() {
		return _votes;
	}

	/**
	 * @see #getVotes()
	 */
	public de.haumacher.phoneblock.db.model.RatingInfo setVotes(int value) {
		internalSetVotes(value);
		return this;
	}

	/** Internal setter for {@link #getVotes()} without chain call utility. */
	protected final void internalSetVotes(int value) {
		_listener.beforeSet(this, VOTES__PROP, value);
		_votes = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.model.RatingInfo registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.model.RatingInfo unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return RATING_INFO__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			PHONE__PROP, 
			RATING__PROP, 
			VOTES__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case PHONE__PROP: return getPhone();
			case RATING__PROP: return getRating();
			case VOTES__PROP: return getVotes();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case PHONE__PROP: internalSetPhone((String) value); break;
			case RATING__PROP: internalSetRating((de.haumacher.phoneblock.db.model.Rating) value); break;
			case VOTES__PROP: internalSetVotes((int) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.model.RatingInfo readRatingInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.RatingInfo result = new de.haumacher.phoneblock.db.model.RatingInfo();
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
		out.name(PHONE__PROP);
		out.value(getPhone());
		out.name(RATING__PROP);
		getRating().writeTo(out);
		out.name(VOTES__PROP);
		out.value(getVotes());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE__PROP: setPhone(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case RATING__PROP: setRating(de.haumacher.phoneblock.db.model.Rating.readRating(in)); break;
			case VOTES__PROP: setVotes(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.db.model.RatingInfo} type. */
	public static final String RATING_INFO__XML_ELEMENT = "rating-info";

	/** XML attribute or element name of a {@link #getPhone} property. */
	private static final String PHONE__XML_ATTR = "phone";

	/** XML attribute or element name of a {@link #getRating} property. */
	private static final String RATING__XML_ATTR = "rating";

	/** XML attribute or element name of a {@link #getVotes} property. */
	private static final String VOTES__XML_ATTR = "votes";

	@Override
	public String getXmlTagName() {
		return RATING_INFO__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(PHONE__XML_ATTR, getPhone());
		out.writeAttribute(RATING__XML_ATTR, getRating().protocolName());
		out.writeAttribute(VOTES__XML_ATTR, Integer.toString(getVotes()));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.db.model.RatingInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RatingInfo readRatingInfo_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		RatingInfo result = new RatingInfo();
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
			case PHONE__XML_ATTR: {
				setPhone(value);
				break;
			}
			case RATING__XML_ATTR: {
				setRating(de.haumacher.phoneblock.db.model.Rating.valueOfProtocol(value));
				break;
			}
			case VOTES__XML_ATTR: {
				setVotes(Integer.parseInt(value));
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
			case PHONE__XML_ATTR: {
				setPhone(in.getElementText());
				break;
			}
			case RATING__XML_ATTR: {
				setRating(de.haumacher.phoneblock.db.model.Rating.valueOfProtocol(in.getElementText()));
				break;
			}
			case VOTES__XML_ATTR: {
				setVotes(Integer.parseInt(in.getElementText()));
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

	/** Creates a new {@link RatingInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RatingInfo readRatingInfo(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.db.model.RatingInfo.readRatingInfo_XmlContent(in);
	}

}
