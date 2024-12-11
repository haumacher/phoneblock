package de.haumacher.phoneblock.app.api.model;

/**
 * A comment posted for a phone number
 */
public class UserComment extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.UserComment} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.UserComment create() {
		return new de.haumacher.phoneblock.app.api.model.UserComment();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.UserComment} type in JSON format. */
	public static final String USER_COMMENT__TYPE = "UserComment";

	/** @see #getId() */
	public static final String ID__PROP = "id";

	/** @see #getPhone() */
	public static final String PHONE__PROP = "phone";

	/** @see #getRating() */
	public static final String RATING__PROP = "rating";

	/** @see #getComment() */
	public static final String COMMENT__PROP = "comment";

	/** @see #getService() */
	public static final String SERVICE__PROP = "service";

	/** @see #getCreated() */
	public static final String CREATED__PROP = "created";

	/** @see #getUp() */
	public static final String UP__PROP = "up";

	/** @see #getDown() */
	public static final String DOWN__PROP = "down";

	private String _id = "";

	private String _phone = "";

	private de.haumacher.phoneblock.app.api.model.Rating _rating = de.haumacher.phoneblock.app.api.model.Rating.A_LEGITIMATE;

	private String _comment = "";

	private String _service = "";

	private long _created = 0L;

	private int _up = 0;

	private int _down = 0;

	/**
	 * Creates a {@link UserComment} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.UserComment#create()
	 */
	protected UserComment() {
		super();
	}

	/**
	 * Technical identifier of this comment.
	 */
	public final String getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.app.api.model.UserComment setId(String value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(String value) {
		_listener.beforeSet(this, ID__PROP, value);
		_id = value;
	}

	/**
	 * The phone number this comment belongs to.
	 */
	public final String getPhone() {
		return _phone;
	}

	/**
	 * @see #getPhone()
	 */
	public de.haumacher.phoneblock.app.api.model.UserComment setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	/** Internal setter for {@link #getPhone()} without chain call utility. */
	protected final void internalSetPhone(String value) {
		_listener.beforeSet(this, PHONE__PROP, value);
		_phone = value;
	}

	/**
	 * The rating of the comment (1 for positive, 5 for negative).
	 */
	public final de.haumacher.phoneblock.app.api.model.Rating getRating() {
		return _rating;
	}

	/**
	 * @see #getRating()
	 */
	public de.haumacher.phoneblock.app.api.model.UserComment setRating(de.haumacher.phoneblock.app.api.model.Rating value) {
		internalSetRating(value);
		return this;
	}

	/** Internal setter for {@link #getRating()} without chain call utility. */
	protected final void internalSetRating(de.haumacher.phoneblock.app.api.model.Rating value) {
		if (value == null) throw new IllegalArgumentException("Property 'rating' cannot be null.");
		_listener.beforeSet(this, RATING__PROP, value);
		_rating = value;
	}

	/**
	 * The comment text
	 */
	public final String getComment() {
		return _comment;
	}

	/**
	 * @see #getComment()
	 */
	public de.haumacher.phoneblock.app.api.model.UserComment setComment(String value) {
		internalSetComment(value);
		return this;
	}

	/** Internal setter for {@link #getComment()} without chain call utility. */
	protected final void internalSetComment(String value) {
		_listener.beforeSet(this, COMMENT__PROP, value);
		_comment = value;
	}

	/**
	 * The source of the comment, <code>phoneblock</code> for comments entered on the web site.
	 */
	public final String getService() {
		return _service;
	}

	/**
	 * @see #getService()
	 */
	public de.haumacher.phoneblock.app.api.model.UserComment setService(String value) {
		internalSetService(value);
		return this;
	}

	/** Internal setter for {@link #getService()} without chain call utility. */
	protected final void internalSetService(String value) {
		_listener.beforeSet(this, SERVICE__PROP, value);
		_service = value;
	}

	/**
	 * The creation date of the comment in milliseconds since epoch.
	 */
	public final long getCreated() {
		return _created;
	}

	/**
	 * @see #getCreated()
	 */
	public de.haumacher.phoneblock.app.api.model.UserComment setCreated(long value) {
		internalSetCreated(value);
		return this;
	}

	/** Internal setter for {@link #getCreated()} without chain call utility. */
	protected final void internalSetCreated(long value) {
		_listener.beforeSet(this, CREATED__PROP, value);
		_created = value;
	}

	/**
	 * Number of "thumbs up" ratings for this comment.
	 */
	public final int getUp() {
		return _up;
	}

	/**
	 * @see #getUp()
	 */
	public de.haumacher.phoneblock.app.api.model.UserComment setUp(int value) {
		internalSetUp(value);
		return this;
	}

	/** Internal setter for {@link #getUp()} without chain call utility. */
	protected final void internalSetUp(int value) {
		_listener.beforeSet(this, UP__PROP, value);
		_up = value;
	}

	/**
	 * Number of "thumbs down" ratings for this comment.
	 */
	public final int getDown() {
		return _down;
	}

	/**
	 * @see #getDown()
	 */
	public de.haumacher.phoneblock.app.api.model.UserComment setDown(int value) {
		internalSetDown(value);
		return this;
	}

	/** Internal setter for {@link #getDown()} without chain call utility. */
	protected final void internalSetDown(int value) {
		_listener.beforeSet(this, DOWN__PROP, value);
		_down = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.app.api.model.UserComment registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.UserComment unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return USER_COMMENT__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ID__PROP, 
			PHONE__PROP, 
			RATING__PROP, 
			COMMENT__PROP, 
			SERVICE__PROP, 
			CREATED__PROP, 
			UP__PROP, 
			DOWN__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ID__PROP: return getId();
			case PHONE__PROP: return getPhone();
			case RATING__PROP: return getRating();
			case COMMENT__PROP: return getComment();
			case SERVICE__PROP: return getService();
			case CREATED__PROP: return getCreated();
			case UP__PROP: return getUp();
			case DOWN__PROP: return getDown();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ID__PROP: internalSetId((String) value); break;
			case PHONE__PROP: internalSetPhone((String) value); break;
			case RATING__PROP: internalSetRating((de.haumacher.phoneblock.app.api.model.Rating) value); break;
			case COMMENT__PROP: internalSetComment((String) value); break;
			case SERVICE__PROP: internalSetService((String) value); break;
			case CREATED__PROP: internalSetCreated((long) value); break;
			case UP__PROP: internalSetUp((int) value); break;
			case DOWN__PROP: internalSetDown((int) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.UserComment readUserComment(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.UserComment result = new de.haumacher.phoneblock.app.api.model.UserComment();
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
		out.name(ID__PROP);
		out.value(getId());
		out.name(PHONE__PROP);
		out.value(getPhone());
		out.name(RATING__PROP);
		getRating().writeTo(out);
		out.name(COMMENT__PROP);
		out.value(getComment());
		out.name(SERVICE__PROP);
		out.value(getService());
		out.name(CREATED__PROP);
		out.value(getCreated());
		out.name(UP__PROP);
		out.value(getUp());
		out.name(DOWN__PROP);
		out.value(getDown());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PHONE__PROP: setPhone(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case RATING__PROP: setRating(de.haumacher.phoneblock.app.api.model.Rating.readRating(in)); break;
			case COMMENT__PROP: setComment(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case SERVICE__PROP: setService(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CREATED__PROP: setCreated(in.nextLong()); break;
			case UP__PROP: setUp(in.nextInt()); break;
			case DOWN__PROP: setDown(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.UserComment} type. */
	public static final String USER_COMMENT__XML_ELEMENT = "user-comment";

	/** XML attribute or element name of a {@link #getId} property. */
	private static final String ID__XML_ATTR = "id";

	/** XML attribute or element name of a {@link #getPhone} property. */
	private static final String PHONE__XML_ATTR = "phone";

	/** XML attribute or element name of a {@link #getRating} property. */
	private static final String RATING__XML_ATTR = "rating";

	/** XML attribute or element name of a {@link #getComment} property. */
	private static final String COMMENT__XML_ATTR = "comment";

	/** XML attribute or element name of a {@link #getService} property. */
	private static final String SERVICE__XML_ATTR = "service";

	/** XML attribute or element name of a {@link #getCreated} property. */
	private static final String CREATED__XML_ATTR = "created";

	/** XML attribute or element name of a {@link #getUp} property. */
	private static final String UP__XML_ATTR = "up";

	/** XML attribute or element name of a {@link #getDown} property. */
	private static final String DOWN__XML_ATTR = "down";

	@Override
	public String getXmlTagName() {
		return USER_COMMENT__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(ID__XML_ATTR, getId());
		out.writeAttribute(PHONE__XML_ATTR, getPhone());
		out.writeAttribute(RATING__XML_ATTR, getRating().protocolName());
		out.writeAttribute(COMMENT__XML_ATTR, getComment());
		out.writeAttribute(SERVICE__XML_ATTR, getService());
		out.writeAttribute(CREATED__XML_ATTR, Long.toString(getCreated()));
		out.writeAttribute(UP__XML_ATTR, Integer.toString(getUp()));
		out.writeAttribute(DOWN__XML_ATTR, Integer.toString(getDown()));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.UserComment} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static UserComment readUserComment_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		UserComment result = new UserComment();
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
			case ID__XML_ATTR: {
				setId(value);
				break;
			}
			case PHONE__XML_ATTR: {
				setPhone(value);
				break;
			}
			case RATING__XML_ATTR: {
				setRating(de.haumacher.phoneblock.app.api.model.Rating.valueOfProtocol(value));
				break;
			}
			case COMMENT__XML_ATTR: {
				setComment(value);
				break;
			}
			case SERVICE__XML_ATTR: {
				setService(value);
				break;
			}
			case CREATED__XML_ATTR: {
				setCreated(Long.parseLong(value));
				break;
			}
			case UP__XML_ATTR: {
				setUp(Integer.parseInt(value));
				break;
			}
			case DOWN__XML_ATTR: {
				setDown(Integer.parseInt(value));
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
			case ID__XML_ATTR: {
				setId(in.getElementText());
				break;
			}
			case PHONE__XML_ATTR: {
				setPhone(in.getElementText());
				break;
			}
			case RATING__XML_ATTR: {
				setRating(de.haumacher.phoneblock.app.api.model.Rating.valueOfProtocol(in.getElementText()));
				break;
			}
			case COMMENT__XML_ATTR: {
				setComment(in.getElementText());
				break;
			}
			case SERVICE__XML_ATTR: {
				setService(in.getElementText());
				break;
			}
			case CREATED__XML_ATTR: {
				setCreated(Long.parseLong(in.getElementText()));
				break;
			}
			case UP__XML_ATTR: {
				setUp(Integer.parseInt(in.getElementText()));
				break;
			}
			case DOWN__XML_ATTR: {
				setDown(Integer.parseInt(in.getElementText()));
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

	/** Creates a new {@link UserComment} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static UserComment readUserComment(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.UserComment.readUserComment_XmlContent(in);
	}

}
