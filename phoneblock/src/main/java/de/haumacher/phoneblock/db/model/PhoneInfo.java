package de.haumacher.phoneblock.db.model;

/**
 * Information about a phone number that is published to the <i>PhoneBlock API</i>.
 */
public class PhoneInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.model.PhoneInfo} instance.
	 */
	public static de.haumacher.phoneblock.db.model.PhoneInfo create() {
		return new de.haumacher.phoneblock.db.model.PhoneInfo();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.model.PhoneInfo} type in JSON format. */
	public static final String PHONE_INFO__TYPE = "PhoneInfo";

	/** @see #getPhone() */
	public static final String PHONE__PROP = "phone";

	/** @see #getRating() */
	public static final String RATING__PROP = "rating";

	/** @see #getVotes() */
	public static final String VOTES__PROP = "votes";

	/** @see #getCnt10() */
	public static final String CNT_10__PROP = "cnt10";

	/** @see #getVotes10() */
	public static final String VOTES_10__PROP = "votes10";

	/** @see #getCnt100() */
	public static final String CNT_100__PROP = "cnt100";

	/** @see #getVotes100() */
	public static final String VOTES_100__PROP = "votes100";

	private String _phone = "";

	private de.haumacher.phoneblock.db.model.Rating _rating = de.haumacher.phoneblock.db.model.Rating.A_LEGITIMATE;

	private int _votes = 0;

	private int _cnt10 = 0;

	private int _votes10 = 0;

	private int _cnt100 = 0;

	private int _votes100 = 0;

	/**
	 * Creates a {@link PhoneInfo} instance.
	 *
	 * @see de.haumacher.phoneblock.db.model.PhoneInfo#create()
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
	public de.haumacher.phoneblock.db.model.PhoneInfo setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	/** Internal setter for {@link #getPhone()} without chain call utility. */
	protected final void internalSetPhone(String value) {
		_listener.beforeSet(this, PHONE__PROP, value);
		_phone = value;
	}

	/**
	 * The rating for the requested phone number.
	 */
	public final de.haumacher.phoneblock.db.model.Rating getRating() {
		return _rating;
	}

	/**
	 * @see #getRating()
	 */
	public de.haumacher.phoneblock.db.model.PhoneInfo setRating(de.haumacher.phoneblock.db.model.Rating value) {
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
	 * The number of votes that support blocking the requested number.
	 */
	public final int getVotes() {
		return _votes;
	}

	/**
	 * @see #getVotes()
	 */
	public de.haumacher.phoneblock.db.model.PhoneInfo setVotes(int value) {
		internalSetVotes(value);
		return this;
	}

	/** Internal setter for {@link #getVotes()} without chain call utility. */
	protected final void internalSetVotes(int value) {
		_listener.beforeSet(this, VOTES__PROP, value);
		_votes = value;
	}

	/**
	 * The number of phone numbers with the the same prefix but a different end digit that are also reported as SPAM.
	 */
	public final int getCnt10() {
		return _cnt10;
	}

	/**
	 * @see #getCnt10()
	 */
	public de.haumacher.phoneblock.db.model.PhoneInfo setCnt10(int value) {
		internalSetCnt10(value);
		return this;
	}

	/** Internal setter for {@link #getCnt10()} without chain call utility. */
	protected final void internalSetCnt10(int value) {
		_listener.beforeSet(this, CNT_10__PROP, value);
		_cnt10 = value;
	}

	/**
	 * The total number of votes against all phone numbers with the the same prefix but a different end digit.
	 */
	public final int getVotes10() {
		return _votes10;
	}

	/**
	 * @see #getVotes10()
	 */
	public de.haumacher.phoneblock.db.model.PhoneInfo setVotes10(int value) {
		internalSetVotes10(value);
		return this;
	}

	/** Internal setter for {@link #getVotes10()} without chain call utility. */
	protected final void internalSetVotes10(int value) {
		_listener.beforeSet(this, VOTES_10__PROP, value);
		_votes10 = value;
	}

	/**
	 * The number of phone numbers with the the same prefix but two different end digits that are also reported as SPAM.
	 *
	 * <p>
	 * This number only considers {@link #getCnt10() blocks of phone numbers} with a minimum fill-ratio.
	 * </p>
	 */
	public final int getCnt100() {
		return _cnt100;
	}

	/**
	 * @see #getCnt100()
	 */
	public de.haumacher.phoneblock.db.model.PhoneInfo setCnt100(int value) {
		internalSetCnt100(value);
		return this;
	}

	/** Internal setter for {@link #getCnt100()} without chain call utility. */
	protected final void internalSetCnt100(int value) {
		_listener.beforeSet(this, CNT_100__PROP, value);
		_cnt100 = value;
	}

	/**
	 * The total number of votes against all phone numbers with the the same prefix but two different end digits. 
	 *
	 * <p>
	 * This number only considers {@link #getCnt10() blocks of phone numbers} with a minimum fill-ratio.
	 * </p>
	 */
	public final int getVotes100() {
		return _votes100;
	}

	/**
	 * @see #getVotes100()
	 */
	public de.haumacher.phoneblock.db.model.PhoneInfo setVotes100(int value) {
		internalSetVotes100(value);
		return this;
	}

	/** Internal setter for {@link #getVotes100()} without chain call utility. */
	protected final void internalSetVotes100(int value) {
		_listener.beforeSet(this, VOTES_100__PROP, value);
		_votes100 = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.model.PhoneInfo registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.model.PhoneInfo unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
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
			RATING__PROP, 
			VOTES__PROP, 
			CNT_10__PROP, 
			VOTES_10__PROP, 
			CNT_100__PROP, 
			VOTES_100__PROP));

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
			case CNT_10__PROP: return getCnt10();
			case VOTES_10__PROP: return getVotes10();
			case CNT_100__PROP: return getCnt100();
			case VOTES_100__PROP: return getVotes100();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case PHONE__PROP: internalSetPhone((String) value); break;
			case RATING__PROP: internalSetRating((de.haumacher.phoneblock.db.model.Rating) value); break;
			case VOTES__PROP: internalSetVotes((int) value); break;
			case CNT_10__PROP: internalSetCnt10((int) value); break;
			case VOTES_10__PROP: internalSetVotes10((int) value); break;
			case CNT_100__PROP: internalSetCnt100((int) value); break;
			case VOTES_100__PROP: internalSetVotes100((int) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.model.PhoneInfo readPhoneInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.PhoneInfo result = new de.haumacher.phoneblock.db.model.PhoneInfo();
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
		out.name(CNT_10__PROP);
		out.value(getCnt10());
		out.name(VOTES_10__PROP);
		out.value(getVotes10());
		out.name(CNT_100__PROP);
		out.value(getCnt100());
		out.name(VOTES_100__PROP);
		out.value(getVotes100());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE__PROP: setPhone(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case RATING__PROP: setRating(de.haumacher.phoneblock.db.model.Rating.readRating(in)); break;
			case VOTES__PROP: setVotes(in.nextInt()); break;
			case CNT_10__PROP: setCnt10(in.nextInt()); break;
			case VOTES_10__PROP: setVotes10(in.nextInt()); break;
			case CNT_100__PROP: setCnt100(in.nextInt()); break;
			case VOTES_100__PROP: setVotes100(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.db.model.PhoneInfo} type. */
	public static final String PHONE_INFO__XML_ELEMENT = "phone-info";

	/** XML attribute or element name of a {@link #getPhone} property. */
	private static final String PHONE__XML_ATTR = "phone";

	/** XML attribute or element name of a {@link #getRating} property. */
	private static final String RATING__XML_ATTR = "rating";

	/** XML attribute or element name of a {@link #getVotes} property. */
	private static final String VOTES__XML_ATTR = "votes";

	/** XML attribute or element name of a {@link #getCnt10} property. */
	private static final String CNT_10__XML_ATTR = "cnt-10";

	/** XML attribute or element name of a {@link #getVotes10} property. */
	private static final String VOTES_10__XML_ATTR = "votes-10";

	/** XML attribute or element name of a {@link #getCnt100} property. */
	private static final String CNT_100__XML_ATTR = "cnt-100";

	/** XML attribute or element name of a {@link #getVotes100} property. */
	private static final String VOTES_100__XML_ATTR = "votes-100";

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
		out.writeAttribute(RATING__XML_ATTR, getRating().protocolName());
		out.writeAttribute(VOTES__XML_ATTR, Integer.toString(getVotes()));
		out.writeAttribute(CNT_10__XML_ATTR, Integer.toString(getCnt10()));
		out.writeAttribute(VOTES_10__XML_ATTR, Integer.toString(getVotes10()));
		out.writeAttribute(CNT_100__XML_ATTR, Integer.toString(getCnt100()));
		out.writeAttribute(VOTES_100__XML_ATTR, Integer.toString(getVotes100()));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.db.model.PhoneInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
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
			case RATING__XML_ATTR: {
				setRating(de.haumacher.phoneblock.db.model.Rating.valueOfProtocol(value));
				break;
			}
			case VOTES__XML_ATTR: {
				setVotes(Integer.parseInt(value));
				break;
			}
			case CNT_10__XML_ATTR: {
				setCnt10(Integer.parseInt(value));
				break;
			}
			case VOTES_10__XML_ATTR: {
				setVotes10(Integer.parseInt(value));
				break;
			}
			case CNT_100__XML_ATTR: {
				setCnt100(Integer.parseInt(value));
				break;
			}
			case VOTES_100__XML_ATTR: {
				setVotes100(Integer.parseInt(value));
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
			case CNT_10__XML_ATTR: {
				setCnt10(Integer.parseInt(in.getElementText()));
				break;
			}
			case VOTES_10__XML_ATTR: {
				setVotes10(Integer.parseInt(in.getElementText()));
				break;
			}
			case CNT_100__XML_ATTR: {
				setCnt100(Integer.parseInt(in.getElementText()));
				break;
			}
			case VOTES_100__XML_ATTR: {
				setVotes100(Integer.parseInt(in.getElementText()));
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
		return de.haumacher.phoneblock.db.model.PhoneInfo.readPhoneInfo_XmlContent(in);
	}

}
