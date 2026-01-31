package de.haumacher.phoneblock.app.api.model;

/**
 * Information about a phone number that is published to the <i>PhoneBlock API</i>.
 */
public class PhoneInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.PhoneInfo} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.PhoneInfo create() {
		return new de.haumacher.phoneblock.app.api.model.PhoneInfo();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.PhoneInfo} type in JSON format. */
	public static final String PHONE_INFO__TYPE = "PhoneInfo";

	/** @see #getPhone() */
	public static final String PHONE__PROP = "phone";

	/** @see #getVotes() */
	public static final String VOTES__PROP = "votes";

	/** @see #getRating() */
	public static final String RATING__PROP = "rating";

	/** @see #getVotesWildcard() */
	public static final String VOTES_WILDCARD__PROP = "votesWildcard";

	/** @see #isWhiteListed() */
	public static final String WHITE_LISTED__PROP = "whiteListed";

	/** @see #isArchived() */
	public static final String ARCHIVED__PROP = "archived";

	/** @see #getDateAdded() */
	public static final String DATE_ADDED__PROP = "dateAdded";

	/** @see #getLastUpdate() */
	public static final String LAST_UPDATE__PROP = "lastUpdate";

	/** @see #getLabel() */
	public static final String LABEL__PROP = "label";

	/** @see #getLocation() */
	public static final String LOCATION__PROP = "location";

	private String _phone = "";

	private int _votes = 0;

	private de.haumacher.phoneblock.app.api.model.Rating _rating = de.haumacher.phoneblock.app.api.model.Rating.A_LEGITIMATE;

	private int _votesWildcard = 0;

	private boolean _whiteListed = false;

	private boolean _archived = false;

	private long _dateAdded = 0L;

	private long _lastUpdate = 0L;

	private String _label = null;

	private String _location = null;

	/**
	 * Creates a {@link PhoneInfo} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.PhoneInfo#create()
	 */
	protected PhoneInfo() {
		super();
	}

	/**
	 * The number being requested.
	 */
	public final String getPhone() {
		return _phone;
	}

	/**
	 * @see #getPhone()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	/** Internal setter for {@link #getPhone()} without chain call utility. */
	protected final void internalSetPhone(String value) {
		_listener.beforeSet(this, PHONE__PROP, value);
		_phone = value;
	}

	/**
	 * The number of votes that support blocking the requested number.
	 */
	public final int getVotes() {
		return _votes;
	}

	/**
	 * @see #getVotes()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setVotes(int value) {
		internalSetVotes(value);
		return this;
	}

	/** Internal setter for {@link #getVotes()} without chain call utility. */
	protected final void internalSetVotes(int value) {
		_listener.beforeSet(this, VOTES__PROP, value);
		_votes = value;
	}

	/**
	 * The rating for the requested phone number.
	 */
	public final de.haumacher.phoneblock.app.api.model.Rating getRating() {
		return _rating;
	}

	/**
	 * @see #getRating()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setRating(de.haumacher.phoneblock.app.api.model.Rating value) {
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
	 * The number of votes when also considering votes for numbers that have all but the last two digits in common with the requested number.
	 *
	 * <p>
	 * Votes to those near-by numbers are only considered, when the density of SPAM numbers around the requested number is found to be high.
	 * </p>
	 */
	public final int getVotesWildcard() {
		return _votesWildcard;
	}

	/**
	 * @see #getVotesWildcard()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setVotesWildcard(int value) {
		internalSetVotesWildcard(value);
		return this;
	}

	/** Internal setter for {@link #getVotesWildcard()} without chain call utility. */
	protected final void internalSetVotesWildcard(int value) {
		_listener.beforeSet(this, VOTES_WILDCARD__PROP, value);
		_votesWildcard = value;
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
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setWhiteListed(boolean value) {
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
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setArchived(boolean value) {
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
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setDateAdded(long value) {
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
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setLastUpdate(long value) {
		internalSetLastUpdate(value);
		return this;
	}

	/** Internal setter for {@link #getLastUpdate()} without chain call utility. */
	protected final void internalSetLastUpdate(long value) {
		_listener.beforeSet(this, LAST_UPDATE__PROP, value);
		_lastUpdate = value;
	}

	/**
	 * The phone number formatted for local display (e.g., "(DE) 030 12345678").
	 */
	public final String getLabel() {
		return _label;
	}

	/**
	 * @see #getLabel()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setLabel(String value) {
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
	 * The city or region from where the call originated (e.g., "Berlin").
	 */
	public final String getLocation() {
		return _location;
	}

	/**
	 * @see #getLocation()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setLocation(String value) {
		internalSetLocation(value);
		return this;
	}

	/** Internal setter for {@link #getLocation()} without chain call utility. */
	protected final void internalSetLocation(String value) {
		_listener.beforeSet(this, LOCATION__PROP, value);
		_location = value;
	}

	/**
	 * Checks, whether {@link #getLocation()} has a value.
	 */
	public final boolean hasLocation() {
		return _location != null;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.app.api.model.PhoneInfo registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.PhoneInfo unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return PHONE_INFO__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			PHONE__PROP, 
			VOTES__PROP, 
			RATING__PROP, 
			VOTES_WILDCARD__PROP, 
			WHITE_LISTED__PROP, 
			ARCHIVED__PROP, 
			DATE_ADDED__PROP, 
			LAST_UPDATE__PROP, 
			LABEL__PROP, 
			LOCATION__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case PHONE__PROP: return getPhone();
			case VOTES__PROP: return getVotes();
			case RATING__PROP: return getRating();
			case VOTES_WILDCARD__PROP: return getVotesWildcard();
			case WHITE_LISTED__PROP: return isWhiteListed();
			case ARCHIVED__PROP: return isArchived();
			case DATE_ADDED__PROP: return getDateAdded();
			case LAST_UPDATE__PROP: return getLastUpdate();
			case LABEL__PROP: return getLabel();
			case LOCATION__PROP: return getLocation();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case PHONE__PROP: internalSetPhone((String) value); break;
			case VOTES__PROP: internalSetVotes((int) value); break;
			case RATING__PROP: internalSetRating((de.haumacher.phoneblock.app.api.model.Rating) value); break;
			case VOTES_WILDCARD__PROP: internalSetVotesWildcard((int) value); break;
			case WHITE_LISTED__PROP: internalSetWhiteListed((boolean) value); break;
			case ARCHIVED__PROP: internalSetArchived((boolean) value); break;
			case DATE_ADDED__PROP: internalSetDateAdded((long) value); break;
			case LAST_UPDATE__PROP: internalSetLastUpdate((long) value); break;
			case LABEL__PROP: internalSetLabel((String) value); break;
			case LOCATION__PROP: internalSetLocation((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.PhoneInfo readPhoneInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.PhoneInfo result = new de.haumacher.phoneblock.app.api.model.PhoneInfo();
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
		out.name(VOTES__PROP);
		out.value(getVotes());
		out.name(RATING__PROP);
		getRating().writeTo(out);
		out.name(VOTES_WILDCARD__PROP);
		out.value(getVotesWildcard());
		out.name(WHITE_LISTED__PROP);
		out.value(isWhiteListed());
		out.name(ARCHIVED__PROP);
		out.value(isArchived());
		out.name(DATE_ADDED__PROP);
		out.value(getDateAdded());
		out.name(LAST_UPDATE__PROP);
		out.value(getLastUpdate());
		if (hasLabel()) {
			out.name(LABEL__PROP);
			out.value(getLabel());
		}
		if (hasLocation()) {
			out.name(LOCATION__PROP);
			out.value(getLocation());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE__PROP: setPhone(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case VOTES__PROP: setVotes(in.nextInt()); break;
			case RATING__PROP: setRating(de.haumacher.phoneblock.app.api.model.Rating.readRating(in)); break;
			case VOTES_WILDCARD__PROP: setVotesWildcard(in.nextInt()); break;
			case WHITE_LISTED__PROP: setWhiteListed(in.nextBoolean()); break;
			case ARCHIVED__PROP: setArchived(in.nextBoolean()); break;
			case DATE_ADDED__PROP: setDateAdded(in.nextLong()); break;
			case LAST_UPDATE__PROP: setLastUpdate(in.nextLong()); break;
			case LABEL__PROP: setLabel(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LOCATION__PROP: setLocation(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.PhoneInfo} type. */
	public static final String PHONE_INFO__XML_ELEMENT = "phone-info";

	/** XML attribute or element name of a {@link #getPhone} property. */
	private static final String PHONE__XML_ATTR = "phone";

	/** XML attribute or element name of a {@link #getVotes} property. */
	private static final String VOTES__XML_ATTR = "votes";

	/** XML attribute or element name of a {@link #getRating} property. */
	private static final String RATING__XML_ATTR = "rating";

	/** XML attribute or element name of a {@link #getVotesWildcard} property. */
	private static final String VOTES_WILDCARD__XML_ATTR = "votes-wildcard";

	/** XML attribute or element name of a {@link #isWhiteListed} property. */
	private static final String WHITE_LISTED__XML_ATTR = "white-listed";

	/** XML attribute or element name of a {@link #isArchived} property. */
	private static final String ARCHIVED__XML_ATTR = "archived";

	/** XML attribute or element name of a {@link #getDateAdded} property. */
	private static final String DATE_ADDED__XML_ATTR = "date-added";

	/** XML attribute or element name of a {@link #getLastUpdate} property. */
	private static final String LAST_UPDATE__XML_ATTR = "last-update";

	/** XML attribute or element name of a {@link #getLabel} property. */
	private static final String LABEL__XML_ATTR = "label";

	/** XML attribute or element name of a {@link #getLocation} property. */
	private static final String LOCATION__XML_ATTR = "location";

	@Override
	public String getXmlTagName() {
		return PHONE_INFO__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(PHONE__XML_ATTR, getPhone());
		out.writeAttribute(VOTES__XML_ATTR, Integer.toString(getVotes()));
		out.writeAttribute(RATING__XML_ATTR, getRating().protocolName());
		out.writeAttribute(VOTES_WILDCARD__XML_ATTR, Integer.toString(getVotesWildcard()));
		out.writeAttribute(WHITE_LISTED__XML_ATTR, Boolean.toString(isWhiteListed()));
		out.writeAttribute(ARCHIVED__XML_ATTR, Boolean.toString(isArchived()));
		out.writeAttribute(DATE_ADDED__XML_ATTR, Long.toString(getDateAdded()));
		out.writeAttribute(LAST_UPDATE__XML_ATTR, Long.toString(getLastUpdate()));
		out.writeAttribute(LABEL__XML_ATTR, getLabel());
		out.writeAttribute(LOCATION__XML_ATTR, getLocation());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.PhoneInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static PhoneInfo readPhoneInfo_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		PhoneInfo result = new PhoneInfo();
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
			case VOTES__XML_ATTR: {
				setVotes(Integer.parseInt(value));
				break;
			}
			case RATING__XML_ATTR: {
				setRating(de.haumacher.phoneblock.app.api.model.Rating.valueOfProtocol(value));
				break;
			}
			case VOTES_WILDCARD__XML_ATTR: {
				setVotesWildcard(Integer.parseInt(value));
				break;
			}
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
			case LABEL__XML_ATTR: {
				setLabel(value);
				break;
			}
			case LOCATION__XML_ATTR: {
				setLocation(value);
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
			case VOTES__XML_ATTR: {
				setVotes(Integer.parseInt(in.getElementText()));
				break;
			}
			case RATING__XML_ATTR: {
				setRating(de.haumacher.phoneblock.app.api.model.Rating.valueOfProtocol(in.getElementText()));
				break;
			}
			case VOTES_WILDCARD__XML_ATTR: {
				setVotesWildcard(Integer.parseInt(in.getElementText()));
				break;
			}
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
			case LABEL__XML_ATTR: {
				setLabel(in.getElementText());
				break;
			}
			case LOCATION__XML_ATTR: {
				setLocation(in.getElementText());
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

	/** Creates a new {@link PhoneInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static PhoneInfo readPhoneInfo(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.PhoneInfo.readPhoneInfo_XmlContent(in);
	}

}
