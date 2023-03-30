package de.haumacher.phoneblock.app.api.model;

/**
 * Request to a add a new rating for a phone number.
 */
public class RateRequest extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.RateRequest} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.RateRequest create() {
		return new de.haumacher.phoneblock.app.api.model.RateRequest();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.RateRequest} type in JSON format. */
	public static final String RATE_REQUEST__TYPE = "RateRequest";

	/** @see #getPhone() */
	public static final String PHONE__PROP = "phone";

	/** @see #getRating() */
	public static final String RATING__PROP = "rating";

	/** @see #getComment() */
	public static final String COMMENT__PROP = "comment";

	/** Identifier for the property {@link #getPhone()} in binary format. */
	static final int PHONE__ID = 1;

	/** Identifier for the property {@link #getRating()} in binary format. */
	static final int RATING__ID = 2;

	/** Identifier for the property {@link #getComment()} in binary format. */
	static final int COMMENT__ID = 3;

	private String _phone = "";

	private String _rating = "";

	private String _comment = "";

	/**
	 * Creates a {@link RateRequest} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.RateRequest#create()
	 */
	protected RateRequest() {
		super();
	}

	/**
	 * The phone number to rate.
	 */
	public final String getPhone() {
		return _phone;
	}

	/**
	 * @see #getPhone()
	 */
	public de.haumacher.phoneblock.app.api.model.RateRequest setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	/** Internal setter for {@link #getPhone()} without chain call utility. */
	protected final void internalSetPhone(String value) {
		_listener.beforeSet(this, PHONE__PROP, value);
		_phone = value;
	}

	/**
	 * The rating code. Must be one of the the codes defined in {@link de.haumacher.phoneblock.db.model.Rating}.
	 */
	public final String getRating() {
		return _rating;
	}

	/**
	 * @see #getRating()
	 */
	public de.haumacher.phoneblock.app.api.model.RateRequest setRating(String value) {
		internalSetRating(value);
		return this;
	}

	/** Internal setter for {@link #getRating()} without chain call utility. */
	protected final void internalSetRating(String value) {
		_listener.beforeSet(this, RATING__PROP, value);
		_rating = value;
	}

	/**
	 * A user comment describing the call or owner of the phone number.
	 */
	public final String getComment() {
		return _comment;
	}

	/**
	 * @see #getComment()
	 */
	public de.haumacher.phoneblock.app.api.model.RateRequest setComment(String value) {
		internalSetComment(value);
		return this;
	}

	/** Internal setter for {@link #getComment()} without chain call utility. */
	protected final void internalSetComment(String value) {
		_listener.beforeSet(this, COMMENT__PROP, value);
		_comment = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.app.api.model.RateRequest registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.RateRequest unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return RATE_REQUEST__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			PHONE__PROP, 
			RATING__PROP, 
			COMMENT__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case PHONE__PROP: return getPhone();
			case RATING__PROP: return getRating();
			case COMMENT__PROP: return getComment();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case PHONE__PROP: internalSetPhone((String) value); break;
			case RATING__PROP: internalSetRating((String) value); break;
			case COMMENT__PROP: internalSetComment((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.RateRequest readRateRequest(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RateRequest result = new de.haumacher.phoneblock.app.api.model.RateRequest();
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
		out.value(getRating());
		out.name(COMMENT__PROP);
		out.value(getComment());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE__PROP: setPhone(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case RATING__PROP: setRating(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case COMMENT__PROP: setComment(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.beginObject();
		writeFields(out);
		out.endObject();
	}

	/**
	 * Serializes all fields of this instance to the given binary output.
	 *
	 * @param out
	 *        The binary output to write to.
	 * @throws java.io.IOException If writing fails.
	 */
	protected void writeFields(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.name(PHONE__ID);
		out.value(getPhone());
		out.name(RATING__ID);
		out.value(getRating());
		out.name(COMMENT__ID);
		out.value(getComment());
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.RateRequest readRateRequest(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.app.api.model.RateRequest result = de.haumacher.phoneblock.app.api.model.RateRequest.readRateRequest_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.app.api.model.RateRequest} from a polymorphic composition. */
	public static de.haumacher.phoneblock.app.api.model.RateRequest readRateRequest_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.RateRequest result = new RateRequest();
		result.readContent(in);
		return result;
	}

	/** Helper for reading all fields of this instance. */
	protected final void readContent(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		while (in.hasNext()) {
			int field = in.nextName();
			readField(in, field);
		}
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case PHONE__ID: setPhone(in.nextString()); break;
			case RATING__ID: setRating(in.nextString()); break;
			case COMMENT__ID: setComment(in.nextString()); break;
			default: in.skipValue(); 
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.RateRequest} type. */
	public static final String RATE_REQUEST__XML_ELEMENT = "rate-request";

	/** XML attribute or element name of a {@link #getPhone} property. */
	private static final String PHONE__XML_ATTR = "phone";

	/** XML attribute or element name of a {@link #getRating} property. */
	private static final String RATING__XML_ATTR = "rating";

	/** XML attribute or element name of a {@link #getComment} property. */
	private static final String COMMENT__XML_ATTR = "comment";

	@Override
	public String getXmlTagName() {
		return RATE_REQUEST__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(PHONE__XML_ATTR, getPhone());
		out.writeAttribute(RATING__XML_ATTR, getRating());
		out.writeAttribute(COMMENT__XML_ATTR, getComment());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.RateRequest} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RateRequest readRateRequest_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		RateRequest result = new RateRequest();
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
				setRating(value);
				break;
			}
			case COMMENT__XML_ATTR: {
				setComment(value);
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
				setRating(in.getElementText());
				break;
			}
			case COMMENT__XML_ATTR: {
				setComment(in.getElementText());
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

	/** Creates a new {@link RateRequest} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static RateRequest readRateRequest(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.RateRequest.readRateRequest_XmlContent(in);
	}

}
