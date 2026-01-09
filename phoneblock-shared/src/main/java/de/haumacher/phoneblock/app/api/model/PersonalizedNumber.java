package de.haumacher.phoneblock.app.api.model;

/**
 * Entry in a personalized number list with optional comment.
 */
public class PersonalizedNumber extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.PersonalizedNumber} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.PersonalizedNumber create() {
		return new de.haumacher.phoneblock.app.api.model.PersonalizedNumber();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.PersonalizedNumber} type in JSON format. */
	public static final String PERSONALIZED_NUMBER__TYPE = "PersonalizedNumber";

	/** @see #getPhone() */
	public static final String PHONE__PROP = "phone";

	/** @see #getLabel() */
	public static final String LABEL__PROP = "label";

	/** @see #getComment() */
	public static final String COMMENT__PROP = "comment";

	/** @see #getRating() */
	public static final String RATING__PROP = "rating";

	private String _phone = "";

	private String _label = null;

	private String _comment = null;

	private de.haumacher.phoneblock.app.api.model.Rating _rating = null;

	/**
	 * Creates a {@link PersonalizedNumber} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.PersonalizedNumber#create()
	 */
	protected PersonalizedNumber() {
		super();
	}

	/**
	 * The phone number in international format (for API communication).
	 */
	public final String getPhone() {
		return _phone;
	}

	/**
	 * @see #getPhone()
	 */
	public de.haumacher.phoneblock.app.api.model.PersonalizedNumber setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	/** Internal setter for {@link #getPhone()} without chain call utility. */
	protected final void internalSetPhone(String value) {
		_listener.beforeSet(this, PHONE__PROP, value);
		_phone = value;
	}

	/**
	 * The phone number formatted according to user's locale/country (for display).
	 */
	public final String getLabel() {
		return _label;
	}

	/**
	 * @see #getLabel()
	 */
	public de.haumacher.phoneblock.app.api.model.PersonalizedNumber setLabel(String value) {
		internalSetLabel(value);
		return this;
	}

	/** Internal setter for {@link #getLabel()} without chain call utility. */
	protected final void internalSetLabel(String value) {
		_listener.beforeSet(this, LABEL__PROP, value);
		_label = value;
	}

	/**
	 * Checks, whether {@link #getLabel()} has a value.
	 */
	public final boolean hasLabel() {
		return _label != null;
	}

	/**
	 * User's comment for this number (may be null).
	 */
	public final String getComment() {
		return _comment;
	}

	/**
	 * @see #getComment()
	 */
	public de.haumacher.phoneblock.app.api.model.PersonalizedNumber setComment(String value) {
		internalSetComment(value);
		return this;
	}

	/** Internal setter for {@link #getComment()} without chain call utility. */
	protected final void internalSetComment(String value) {
		_listener.beforeSet(this, COMMENT__PROP, value);
		_comment = value;
	}

	/**
	 * Checks, whether {@link #getComment()} has a value.
	 */
	public final boolean hasComment() {
		return _comment != null;
	}

	/**
	 * User's rating for this number (may be null).
	 */
	public final de.haumacher.phoneblock.app.api.model.Rating getRating() {
		return _rating;
	}

	/**
	 * @see #getRating()
	 */
	public de.haumacher.phoneblock.app.api.model.PersonalizedNumber setRating(de.haumacher.phoneblock.app.api.model.Rating value) {
		internalSetRating(value);
		return this;
	}

	/** Internal setter for {@link #getRating()} without chain call utility. */
	protected final void internalSetRating(de.haumacher.phoneblock.app.api.model.Rating value) {
		_listener.beforeSet(this, RATING__PROP, value);
		_rating = value;
	}

	/**
	 * Checks, whether {@link #getRating()} has a value.
	 */
	public final boolean hasRating() {
		return _rating != null;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.app.api.model.PersonalizedNumber registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.PersonalizedNumber unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return PERSONALIZED_NUMBER__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			PHONE__PROP, 
			LABEL__PROP, 
			COMMENT__PROP, 
			RATING__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case PHONE__PROP: return getPhone();
			case LABEL__PROP: return getLabel();
			case COMMENT__PROP: return getComment();
			case RATING__PROP: return getRating();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case PHONE__PROP: internalSetPhone((String) value); break;
			case LABEL__PROP: internalSetLabel((String) value); break;
			case COMMENT__PROP: internalSetComment((String) value); break;
			case RATING__PROP: internalSetRating((de.haumacher.phoneblock.app.api.model.Rating) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.PersonalizedNumber readPersonalizedNumber(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.PersonalizedNumber result = new de.haumacher.phoneblock.app.api.model.PersonalizedNumber();
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
		if (hasLabel()) {
			out.name(LABEL__PROP);
			out.value(getLabel());
		}
		if (hasComment()) {
			out.name(COMMENT__PROP);
			out.value(getComment());
		}
		if (hasRating()) {
			out.name(RATING__PROP);
			getRating().writeTo(out);
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE__PROP: setPhone(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LABEL__PROP: setLabel(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case COMMENT__PROP: setComment(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case RATING__PROP: setRating(de.haumacher.phoneblock.app.api.model.Rating.readRating(in)); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.PersonalizedNumber} type. */
	public static final String PERSONALIZED_NUMBER__XML_ELEMENT = "personalized-number";

	/** XML attribute or element name of a {@link #getPhone} property. */
	private static final String PHONE__XML_ATTR = "phone";

	/** XML attribute or element name of a {@link #getLabel} property. */
	private static final String LABEL__XML_ATTR = "label";

	/** XML attribute or element name of a {@link #getComment} property. */
	private static final String COMMENT__XML_ATTR = "comment";

	/** XML attribute or element name of a {@link #getRating} property. */
	private static final String RATING__XML_ATTR = "rating";

	@Override
	public String getXmlTagName() {
		return PERSONALIZED_NUMBER__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(PHONE__XML_ATTR, getPhone());
		out.writeAttribute(LABEL__XML_ATTR, getLabel());
		out.writeAttribute(COMMENT__XML_ATTR, getComment());
		out.writeAttribute(RATING__XML_ATTR, getRating().protocolName());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.PersonalizedNumber} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static PersonalizedNumber readPersonalizedNumber_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		PersonalizedNumber result = new PersonalizedNumber();
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
			case LABEL__XML_ATTR: {
				setLabel(value);
				break;
			}
			case COMMENT__XML_ATTR: {
				setComment(value);
				break;
			}
			case RATING__XML_ATTR: {
				setRating(de.haumacher.phoneblock.app.api.model.Rating.valueOfProtocol(value));
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
			case LABEL__XML_ATTR: {
				setLabel(in.getElementText());
				break;
			}
			case COMMENT__XML_ATTR: {
				setComment(in.getElementText());
				break;
			}
			case RATING__XML_ATTR: {
				setRating(de.haumacher.phoneblock.app.api.model.Rating.valueOfProtocol(in.getElementText()));
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

	/** Creates a new {@link PersonalizedNumber} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static PersonalizedNumber readPersonalizedNumber(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.PersonalizedNumber.readPersonalizedNumber_XmlContent(in);
	}

}
