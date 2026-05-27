package de.haumacher.phoneblock.app.api.model;

/**
 * Information about a phone number that is published to the <i>PhoneBlock API</i>.
 */
public class PhoneInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.PhoneInfo} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.PhoneInfo create() {
		return new de.haumacher.phoneblock.app.api.model.PhoneInfo();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.PhoneInfo} type in JSON format. */
	public static final String PHONE_INFO__TYPE = "PhoneInfo";

	/** @see #getPhone() */
	private static final String PHONE__PROP = "phone";

	/** @see #getVotes() */
	private static final String VOTES__PROP = "votes";

	/** @see #getRating() */
	private static final String RATING__PROP = "rating";

	/** @see #getVotesWildcard() */
	private static final String VOTES_WILDCARD__PROP = "votesWildcard";

	/** @see #isWhiteListed() */
	private static final String WHITE_LISTED__PROP = "whiteListed";

	/** @see #isBlackListed() */
	private static final String BLACK_LISTED__PROP = "blackListed";

	/** @see #getDateAdded() */
	private static final String DATE_ADDED__PROP = "dateAdded";

	/** @see #getLastUpdate() */
	private static final String LAST_UPDATE__PROP = "lastUpdate";

	/** @see #getLabel() */
	private static final String LABEL__PROP = "label";

	/** @see #getLocation() */
	private static final String LOCATION__PROP = "location";

	/** @see #getUserComment() */
	private static final String USER_COMMENT__PROP = "userComment";

	/** @see #getHeat() */
	private static final String HEAT__PROP = "heat";

	/** @see #getSpamConfidence() */
	private static final String SPAM_CONFIDENCE__PROP = "spamConfidence";

	private String _phone = "";

	private int _votes = 0;

	private de.haumacher.phoneblock.app.api.model.Rating _rating = de.haumacher.phoneblock.app.api.model.Rating.A_LEGITIMATE;

	private int _votesWildcard = 0;

	private boolean _whiteListed = false;

	private boolean _blackListed = false;

	private long _dateAdded = 0L;

	private long _lastUpdate = 0L;

	private String _label = null;

	private String _location = null;

	private String _userComment = null;

	private double _heat = 0.0d;

	private int _spamConfidence = 0;

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
		_whiteListed = value;
	}

	/**
	 * Whether this number is on the requesting user's personal block list.
	 */
	public final boolean isBlackListed() {
		return _blackListed;
	}

	/**
	 * @see #isBlackListed()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setBlackListed(boolean value) {
		internalSetBlackListed(value);
		return this;
	}

	/** Internal setter for {@link #isBlackListed()} without chain call utility. */
	protected final void internalSetBlackListed(boolean value) {
		_blackListed = value;
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
		_location = value;
	}

	/**
	 * Checks, whether {@link #getLocation()} has a value.
	 */
	public final boolean hasLocation() {
		return _location != null;
	}

	/**
	 * The comment that the requesting user has previously stored for this number, or <code>null</code>.
	 *
	 * <p>
	 * Only populated when the request is authenticated and a comment was previously submitted by the same user (e.g., via the {@code /rate} endpoint). This is the user's own note about the number, not a community comment.
	 * </p>
	 */
	public final String getUserComment() {
		return _userComment;
	}

	/**
	 * @see #getUserComment()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setUserComment(String value) {
		internalSetUserComment(value);
		return this;
	}

	/** Internal setter for {@link #getUserComment()} without chain call utility. */
	protected final void internalSetUserComment(String value) {
		_userComment = value;
	}

	/**
	 * Checks, whether {@link #getUserComment()} has a value.
	 */
	public final boolean hasUserComment() {
		return _userComment != null;
	}

	/**
	 * Recent-activity score derived from the confidence model (issue #300).
	 *
	 * <p>
	 * The decayed <code>HEAT</code> EMA at request time — how loud this number is <em>right now</em>, with a half-life of about two weeks. At a steady rate of <i>r</i> reports per day it converges to ≈ <i>r</i> · τ_heat, so the value is directly interpretable. Drives ranking on space-limited lists (Fritz!Box phonebook, dongle) and Heat-based archiving — it is <em>not</em> the spam-or-not signal; that is {@link #getSpamConfidence()}.
	 * </p>
	 */
	public final double getHeat() {
		return _heat;
	}

	/**
	 * @see #getHeat()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setHeat(double value) {
		internalSetHeat(value);
		return this;
	}

	/** Internal setter for {@link #getHeat()} without chain call utility. */
	protected final void internalSetHeat(double value) {
		_heat = value;
	}

	/**
	 * Confidence (0–100) that this number is spam, derived from the confidence model (issue #300).
	 *
	 * <p>
	 * Computed as a Wilson lower bound on <code>SPAM_EVIDENCE / (SPAM_EVIDENCE + LEGIT_EVIDENCE)</code>, decayed to the request time on a ~4-month half-life. The evidence mass <code>SPAM + LEGIT</code> is the denominator, so a number with two spam reports and nothing else reads low confidence ("maybe accidental"), and a hotly-contested number with 1000 reports each side also reads low ("disputed"). High values require both directional evidence <em>and</em> mass.
	 * </p>
	 */
	public final int getSpamConfidence() {
		return _spamConfidence;
	}

	/**
	 * @see #getSpamConfidence()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneInfo setSpamConfidence(int value) {
		internalSetSpamConfidence(value);
		return this;
	}

	/** Internal setter for {@link #getSpamConfidence()} without chain call utility. */
	protected final void internalSetSpamConfidence(int value) {
		_spamConfidence = value;
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
		out.name(BLACK_LISTED__PROP);
		out.value(isBlackListed());
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
		if (hasUserComment()) {
			out.name(USER_COMMENT__PROP);
			out.value(getUserComment());
		}
		out.name(HEAT__PROP);
		out.value(getHeat());
		out.name(SPAM_CONFIDENCE__PROP);
		out.value(getSpamConfidence());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE__PROP: setPhone(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case VOTES__PROP: setVotes(in.nextInt()); break;
			case RATING__PROP: setRating(de.haumacher.phoneblock.app.api.model.Rating.readRating(in)); break;
			case VOTES_WILDCARD__PROP: setVotesWildcard(in.nextInt()); break;
			case WHITE_LISTED__PROP: setWhiteListed(in.nextBoolean()); break;
			case BLACK_LISTED__PROP: setBlackListed(in.nextBoolean()); break;
			case DATE_ADDED__PROP: setDateAdded(in.nextLong()); break;
			case LAST_UPDATE__PROP: setLastUpdate(in.nextLong()); break;
			case LABEL__PROP: setLabel(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LOCATION__PROP: setLocation(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case USER_COMMENT__PROP: setUserComment(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case HEAT__PROP: setHeat(in.nextDouble()); break;
			case SPAM_CONFIDENCE__PROP: setSpamConfidence(in.nextInt()); break;
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

	/** XML attribute or element name of a {@link #isBlackListed} property. */
	private static final String BLACK_LISTED__XML_ATTR = "black-listed";

	/** XML attribute or element name of a {@link #getDateAdded} property. */
	private static final String DATE_ADDED__XML_ATTR = "date-added";

	/** XML attribute or element name of a {@link #getLastUpdate} property. */
	private static final String LAST_UPDATE__XML_ATTR = "last-update";

	/** XML attribute or element name of a {@link #getLabel} property. */
	private static final String LABEL__XML_ATTR = "label";

	/** XML attribute or element name of a {@link #getLocation} property. */
	private static final String LOCATION__XML_ATTR = "location";

	/** XML attribute or element name of a {@link #getUserComment} property. */
	private static final String USER_COMMENT__XML_ATTR = "user-comment";

	/** XML attribute or element name of a {@link #getHeat} property. */
	private static final String HEAT__XML_ATTR = "heat";

	/** XML attribute or element name of a {@link #getSpamConfidence} property. */
	private static final String SPAM_CONFIDENCE__XML_ATTR = "spam-confidence";

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
		out.writeAttribute(BLACK_LISTED__XML_ATTR, Boolean.toString(isBlackListed()));
		out.writeAttribute(DATE_ADDED__XML_ATTR, Long.toString(getDateAdded()));
		out.writeAttribute(LAST_UPDATE__XML_ATTR, Long.toString(getLastUpdate()));
		out.writeAttribute(LABEL__XML_ATTR, getLabel());
		out.writeAttribute(LOCATION__XML_ATTR, getLocation());
		out.writeAttribute(USER_COMMENT__XML_ATTR, getUserComment());
		out.writeAttribute(HEAT__XML_ATTR, Double.toString(getHeat()));
		out.writeAttribute(SPAM_CONFIDENCE__XML_ATTR, Integer.toString(getSpamConfidence()));
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
			case BLACK_LISTED__XML_ATTR: {
				setBlackListed(Boolean.parseBoolean(value));
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
			case USER_COMMENT__XML_ATTR: {
				setUserComment(value);
				break;
			}
			case HEAT__XML_ATTR: {
				setHeat(Double.parseDouble(value));
				break;
			}
			case SPAM_CONFIDENCE__XML_ATTR: {
				setSpamConfidence(Integer.parseInt(value));
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
			case BLACK_LISTED__XML_ATTR: {
				setBlackListed(Boolean.parseBoolean(in.getElementText()));
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
			case USER_COMMENT__XML_ATTR: {
				setUserComment(in.getElementText());
				break;
			}
			case HEAT__XML_ATTR: {
				setHeat(Double.parseDouble(in.getElementText()));
				break;
			}
			case SPAM_CONFIDENCE__XML_ATTR: {
				setSpamConfidence(Integer.parseInt(in.getElementText()));
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
