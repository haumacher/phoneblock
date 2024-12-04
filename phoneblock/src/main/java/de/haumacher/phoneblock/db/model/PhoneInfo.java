package de.haumacher.phoneblock.db.model;

/**
 * Information about a phone number that is published to the <i>PhoneBlock API</i>.
 */
public class PhoneInfo extends PhoneSummary {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.model.PhoneInfo} instance.
	 */
	public static de.haumacher.phoneblock.db.model.PhoneInfo create() {
		return new de.haumacher.phoneblock.db.model.PhoneInfo();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.model.PhoneInfo} type in JSON format. */
	public static final String PHONE_INFO__TYPE = "PhoneInfo";

	/** @see #isWhiteListed() */
	public static final String WHITE_LISTED__PROP = "whiteListed";

	/** @see #isArchived() */
	public static final String ARCHIVED__PROP = "archived";

	/** @see #getDateAdded() */
	public static final String DATE_ADDED__PROP = "dateAdded";

	/** @see #getLastUpdate() */
	public static final String LAST_UPDATE__PROP = "lastUpdate";

	private boolean _whiteListed = false;

	private boolean _archived = false;

	private long _dateAdded = 0L;

	private long _lastUpdate = 0L;

	/**
	 * Creates a {@link PhoneInfo} instance.
	 *
	 * @see de.haumacher.phoneblock.db.model.PhoneInfo#create()
	 */
	protected PhoneInfo() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.PHONE_INFO;
	}

	/**
	 * Whether this number is on the white list and therefore cannot receive votes.
	 */
	public final boolean isWhiteListed() {
		return _whiteListed;
	}

	/**
	 * @see #isWhiteListed()
	 */
	public de.haumacher.phoneblock.db.model.PhoneInfo setWhiteListed(boolean value) {
		internalSetWhiteListed(value);
		return this;
	}

	/** Internal setter for {@link #isWhiteListed()} without chain call utility. */
	protected final void internalSetWhiteListed(boolean value) {
		_listener.beforeSet(this, WHITE_LISTED__PROP, value);
		_whiteListed = value;
	}

	/**
	 * Whether this number no longer is on the blocklist, because no votes have been received for a long time.
	 */
	public final boolean isArchived() {
		return _archived;
	}

	/**
	 * @see #isArchived()
	 */
	public de.haumacher.phoneblock.db.model.PhoneInfo setArchived(boolean value) {
		internalSetArchived(value);
		return this;
	}

	/** Internal setter for {@link #isArchived()} without chain call utility. */
	protected final void internalSetArchived(boolean value) {
		_listener.beforeSet(this, ARCHIVED__PROP, value);
		_archived = value;
	}

	/**
	 * Date when this number was added to the SPAM database (in milliseconds since epoch).
	 */
	public final long getDateAdded() {
		return _dateAdded;
	}

	/**
	 * @see #getDateAdded()
	 */
	public de.haumacher.phoneblock.db.model.PhoneInfo setDateAdded(long value) {
		internalSetDateAdded(value);
		return this;
	}

	/** Internal setter for {@link #getDateAdded()} without chain call utility. */
	protected final void internalSetDateAdded(long value) {
		_listener.beforeSet(this, DATE_ADDED__PROP, value);
		_dateAdded = value;
	}

	/**
	 * Date when the last report for this number was received (in milliseconds since epoch).
	 */
	public final long getLastUpdate() {
		return _lastUpdate;
	}

	/**
	 * @see #getLastUpdate()
	 */
	public de.haumacher.phoneblock.db.model.PhoneInfo setLastUpdate(long value) {
		internalSetLastUpdate(value);
		return this;
	}

	/** Internal setter for {@link #getLastUpdate()} without chain call utility. */
	protected final void internalSetLastUpdate(long value) {
		_listener.beforeSet(this, LAST_UPDATE__PROP, value);
		_lastUpdate = value;
	}

	@Override
	public de.haumacher.phoneblock.db.model.PhoneInfo setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.PhoneInfo setVotes(int value) {
		internalSetVotes(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.PhoneInfo setRating(de.haumacher.phoneblock.db.model.Rating value) {
		internalSetRating(value);
		return this;
	}

	@Override
	public String jsonType() {
		return PHONE_INFO__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			WHITE_LISTED__PROP, 
			ARCHIVED__PROP, 
			DATE_ADDED__PROP, 
			LAST_UPDATE__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case WHITE_LISTED__PROP: return isWhiteListed();
			case ARCHIVED__PROP: return isArchived();
			case DATE_ADDED__PROP: return getDateAdded();
			case LAST_UPDATE__PROP: return getLastUpdate();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case WHITE_LISTED__PROP: internalSetWhiteListed((boolean) value); break;
			case ARCHIVED__PROP: internalSetArchived((boolean) value); break;
			case DATE_ADDED__PROP: internalSetDateAdded((long) value); break;
			case LAST_UPDATE__PROP: internalSetLastUpdate((long) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.model.PhoneInfo readPhoneInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.PhoneInfo result = new de.haumacher.phoneblock.db.model.PhoneInfo();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(WHITE_LISTED__PROP);
		out.value(isWhiteListed());
		out.name(ARCHIVED__PROP);
		out.value(isArchived());
		out.name(DATE_ADDED__PROP);
		out.value(getDateAdded());
		out.name(LAST_UPDATE__PROP);
		out.value(getLastUpdate());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case WHITE_LISTED__PROP: setWhiteListed(in.nextBoolean()); break;
			case ARCHIVED__PROP: setArchived(in.nextBoolean()); break;
			case DATE_ADDED__PROP: setDateAdded(in.nextLong()); break;
			case LAST_UPDATE__PROP: setLastUpdate(in.nextLong()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.db.model.PhoneInfo} type. */
	public static final String PHONE_INFO__XML_ELEMENT = "phone-info";

	/** XML attribute or element name of a {@link #isWhiteListed} property. */
	private static final String WHITE_LISTED__XML_ATTR = "white-listed";

	/** XML attribute or element name of a {@link #isArchived} property. */
	private static final String ARCHIVED__XML_ATTR = "archived";

	/** XML attribute or element name of a {@link #getDateAdded} property. */
	private static final String DATE_ADDED__XML_ATTR = "date-added";

	/** XML attribute or element name of a {@link #getLastUpdate} property. */
	private static final String LAST_UPDATE__XML_ATTR = "last-update";

	@Override
	public String getXmlTagName() {
		return PHONE_INFO__XML_ELEMENT;
	}

	/** Serializes all fields that are written as XML attributes. */
	@Override
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeAttributes(out);
		out.writeAttribute(WHITE_LISTED__XML_ATTR, Boolean.toString(isWhiteListed()));
		out.writeAttribute(ARCHIVED__XML_ATTR, Boolean.toString(isArchived()));
		out.writeAttribute(DATE_ADDED__XML_ATTR, Long.toString(getDateAdded()));
		out.writeAttribute(LAST_UPDATE__XML_ATTR, Long.toString(getLastUpdate()));
	}

	/** Serializes all fields that are written as XML elements. */
	@Override
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeElements(out);
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.db.model.PhoneInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static PhoneInfo readPhoneInfo_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		PhoneInfo result = new PhoneInfo();
		result.readContentXml(in);
		return result;
	}

	@Override
	protected void readFieldXmlAttribute(String name, String value) {
		switch (name) {
			case WHITE_LISTED__XML_ATTR: {
				setWhiteListed(Boolean.parseBoolean(value));
				break;
			}
			case ARCHIVED__XML_ATTR: {
				setArchived(Boolean.parseBoolean(value));
				break;
			}
			case DATE_ADDED__XML_ATTR: {
				setDateAdded(Long.parseLong(value));
				break;
			}
			case LAST_UPDATE__XML_ATTR: {
				setLastUpdate(Long.parseLong(value));
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
			case WHITE_LISTED__XML_ATTR: {
				setWhiteListed(Boolean.parseBoolean(in.getElementText()));
				break;
			}
			case ARCHIVED__XML_ATTR: {
				setArchived(Boolean.parseBoolean(in.getElementText()));
				break;
			}
			case DATE_ADDED__XML_ATTR: {
				setDateAdded(Long.parseLong(in.getElementText()));
				break;
			}
			case LAST_UPDATE__XML_ATTR: {
				setLastUpdate(Long.parseLong(in.getElementText()));
				break;
			}
			default: {
				super.readFieldXmlElement(in, localName);
			}
		}
	}

	/** Creates a new {@link PhoneInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static PhoneInfo readPhoneInfo(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.db.model.PhoneInfo.readPhoneInfo_XmlContent(in);
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.db.model.PhoneSummary.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
