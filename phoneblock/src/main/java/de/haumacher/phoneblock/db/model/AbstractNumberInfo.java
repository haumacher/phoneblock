package de.haumacher.phoneblock.db.model;

public abstract class AbstractNumberInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/** Type codes for the {@link de.haumacher.phoneblock.db.model.AbstractNumberInfo} hierarchy. */
	public enum TypeKind {

		/** Type literal for {@link de.haumacher.phoneblock.db.model.NumberInfo}. */
		NUMBER_INFO,

		/** Type literal for {@link de.haumacher.phoneblock.db.model.NumberHistory}. */
		NUMBER_HISTORY,
		;

	}

	/** Visitor interface for the {@link de.haumacher.phoneblock.db.model.AbstractNumberInfo} hierarchy.*/
	public interface Visitor<R,A,E extends Throwable> {

		/** Visit case for {@link de.haumacher.phoneblock.db.model.NumberInfo}.*/
		R visit(de.haumacher.phoneblock.db.model.NumberInfo self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.db.model.NumberHistory}.*/
		R visit(de.haumacher.phoneblock.db.model.NumberHistory self, A arg) throws E;

	}

	/** @see #getPhone() */
	public static final String PHONE__PROP = "phone";

	/** @see #isActive() */
	public static final String ACTIVE__PROP = "active";

	/** @see #getCalls() */
	public static final String CALLS__PROP = "calls";

	/** @see #getVotes() */
	public static final String VOTES__PROP = "votes";

	/** @see #getRatingLegitimate() */
	public static final String RATING_LEGITIMATE__PROP = "ratingLegitimate";

	/** @see #getRatingPing() */
	public static final String RATING_PING__PROP = "ratingPing";

	/** @see #getRatingPoll() */
	public static final String RATING_POLL__PROP = "ratingPoll";

	/** @see #getRatingAdvertising() */
	public static final String RATING_ADVERTISING__PROP = "ratingAdvertising";

	/** @see #getRatingGamble() */
	public static final String RATING_GAMBLE__PROP = "ratingGamble";

	/** @see #getRatingFraud() */
	public static final String RATING_FRAUD__PROP = "ratingFraud";

	/** @see #getSearches() */
	public static final String SEARCHES__PROP = "searches";

	private String _phone = "";

	private boolean _active = false;

	private int _calls = 0;

	private int _votes = 0;

	private int _ratingLegitimate = 0;

	private int _ratingPing = 0;

	private int _ratingPoll = 0;

	private int _ratingAdvertising = 0;

	private int _ratingGamble = 0;

	private int _ratingFraud = 0;

	private int _searches = 0;

	/**
	 * Creates a {@link AbstractNumberInfo} instance.
	 */
	protected AbstractNumberInfo() {
		super();
	}

	/** The type code of this instance. */
	public abstract TypeKind kind();

	/**
	 * The number being requested.
	 */
	public final String getPhone() {
		return _phone;
	}

	/**
	 * @see #getPhone()
	 */
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	/** Internal setter for {@link #getPhone()} without chain call utility. */
	protected final void internalSetPhone(String value) {
		_listener.beforeSet(this, PHONE__PROP, value);
		_phone = value;
	}

	/**
	 * Whether the number is considered active. Only active numbers are inserted into a blocklist.
	 */
	public final boolean isActive() {
		return _active;
	}

	/**
	 * @see #isActive()
	 */
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setActive(boolean value) {
		internalSetActive(value);
		return this;
	}

	/** Internal setter for {@link #isActive()} without chain call utility. */
	protected final void internalSetActive(boolean value) {
		_listener.beforeSet(this, ACTIVE__PROP, value);
		_active = value;
	}

	public final int getCalls() {
		return _calls;
	}

	/**
	 * @see #getCalls()
	 */
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setCalls(int value) {
		internalSetCalls(value);
		return this;
	}

	/** Internal setter for {@link #getCalls()} without chain call utility. */
	protected final void internalSetCalls(int value) {
		_listener.beforeSet(this, CALLS__PROP, value);
		_calls = value;
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
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setVotes(int value) {
		internalSetVotes(value);
		return this;
	}

	/** Internal setter for {@link #getVotes()} without chain call utility. */
	protected final void internalSetVotes(int value) {
		_listener.beforeSet(this, VOTES__PROP, value);
		_votes = value;
	}

	/**
	 * The number ratings of kind "legitimate".
	 */
	public final int getRatingLegitimate() {
		return _ratingLegitimate;
	}

	/**
	 * @see #getRatingLegitimate()
	 */
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setRatingLegitimate(int value) {
		internalSetRatingLegitimate(value);
		return this;
	}

	/** Internal setter for {@link #getRatingLegitimate()} without chain call utility. */
	protected final void internalSetRatingLegitimate(int value) {
		_listener.beforeSet(this, RATING_LEGITIMATE__PROP, value);
		_ratingLegitimate = value;
	}

	/**
	 * The number ratings of kind "ping".
	 */
	public final int getRatingPing() {
		return _ratingPing;
	}

	/**
	 * @see #getRatingPing()
	 */
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setRatingPing(int value) {
		internalSetRatingPing(value);
		return this;
	}

	/** Internal setter for {@link #getRatingPing()} without chain call utility. */
	protected final void internalSetRatingPing(int value) {
		_listener.beforeSet(this, RATING_PING__PROP, value);
		_ratingPing = value;
	}

	/**
	 * The number ratings of kind "poll".
	 */
	public final int getRatingPoll() {
		return _ratingPoll;
	}

	/**
	 * @see #getRatingPoll()
	 */
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setRatingPoll(int value) {
		internalSetRatingPoll(value);
		return this;
	}

	/** Internal setter for {@link #getRatingPoll()} without chain call utility. */
	protected final void internalSetRatingPoll(int value) {
		_listener.beforeSet(this, RATING_POLL__PROP, value);
		_ratingPoll = value;
	}

	/**
	 * The number ratings of kind "advertising".
	 */
	public final int getRatingAdvertising() {
		return _ratingAdvertising;
	}

	/**
	 * @see #getRatingAdvertising()
	 */
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setRatingAdvertising(int value) {
		internalSetRatingAdvertising(value);
		return this;
	}

	/** Internal setter for {@link #getRatingAdvertising()} without chain call utility. */
	protected final void internalSetRatingAdvertising(int value) {
		_listener.beforeSet(this, RATING_ADVERTISING__PROP, value);
		_ratingAdvertising = value;
	}

	/**
	 * The number ratings of kind "gamble".
	 */
	public final int getRatingGamble() {
		return _ratingGamble;
	}

	/**
	 * @see #getRatingGamble()
	 */
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setRatingGamble(int value) {
		internalSetRatingGamble(value);
		return this;
	}

	/** Internal setter for {@link #getRatingGamble()} without chain call utility. */
	protected final void internalSetRatingGamble(int value) {
		_listener.beforeSet(this, RATING_GAMBLE__PROP, value);
		_ratingGamble = value;
	}

	/**
	 * The number ratings of kind "fraud".
	 */
	public final int getRatingFraud() {
		return _ratingFraud;
	}

	/**
	 * @see #getRatingFraud()
	 */
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setRatingFraud(int value) {
		internalSetRatingFraud(value);
		return this;
	}

	/** Internal setter for {@link #getRatingFraud()} without chain call utility. */
	protected final void internalSetRatingFraud(int value) {
		_listener.beforeSet(this, RATING_FRAUD__PROP, value);
		_ratingFraud = value;
	}

	/**
	 * The number of search request for this number.
	 */
	public final int getSearches() {
		return _searches;
	}

	/**
	 * @see #getSearches()
	 */
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo setSearches(int value) {
		internalSetSearches(value);
		return this;
	}

	/** Internal setter for {@link #getSearches()} without chain call utility. */
	protected final void internalSetSearches(int value) {
		_listener.beforeSet(this, SEARCHES__PROP, value);
		_searches = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.model.AbstractNumberInfo unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			PHONE__PROP, 
			ACTIVE__PROP, 
			CALLS__PROP, 
			VOTES__PROP, 
			RATING_LEGITIMATE__PROP, 
			RATING_PING__PROP, 
			RATING_POLL__PROP, 
			RATING_ADVERTISING__PROP, 
			RATING_GAMBLE__PROP, 
			RATING_FRAUD__PROP, 
			SEARCHES__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case PHONE__PROP: return getPhone();
			case ACTIVE__PROP: return isActive();
			case CALLS__PROP: return getCalls();
			case VOTES__PROP: return getVotes();
			case RATING_LEGITIMATE__PROP: return getRatingLegitimate();
			case RATING_PING__PROP: return getRatingPing();
			case RATING_POLL__PROP: return getRatingPoll();
			case RATING_ADVERTISING__PROP: return getRatingAdvertising();
			case RATING_GAMBLE__PROP: return getRatingGamble();
			case RATING_FRAUD__PROP: return getRatingFraud();
			case SEARCHES__PROP: return getSearches();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case PHONE__PROP: internalSetPhone((String) value); break;
			case ACTIVE__PROP: internalSetActive((boolean) value); break;
			case CALLS__PROP: internalSetCalls((int) value); break;
			case VOTES__PROP: internalSetVotes((int) value); break;
			case RATING_LEGITIMATE__PROP: internalSetRatingLegitimate((int) value); break;
			case RATING_PING__PROP: internalSetRatingPing((int) value); break;
			case RATING_POLL__PROP: internalSetRatingPoll((int) value); break;
			case RATING_ADVERTISING__PROP: internalSetRatingAdvertising((int) value); break;
			case RATING_GAMBLE__PROP: internalSetRatingGamble((int) value); break;
			case RATING_FRAUD__PROP: internalSetRatingFraud((int) value); break;
			case SEARCHES__PROP: internalSetSearches((int) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.model.AbstractNumberInfo readAbstractNumberInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.AbstractNumberInfo result;
		in.beginArray();
		String type = in.nextString();
		switch (type) {
			case NumberInfo.NUMBER_INFO__TYPE: result = de.haumacher.phoneblock.db.model.NumberInfo.readNumberInfo(in); break;
			case NumberHistory.NUMBER_HISTORY__TYPE: result = de.haumacher.phoneblock.db.model.NumberHistory.readNumberHistory(in); break;
			default: in.skipValue(); result = null; break;
		}
		in.endArray();
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		out.beginArray();
		out.value(jsonType());
		writeContent(out);
		out.endArray();
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(PHONE__PROP);
		out.value(getPhone());
		out.name(ACTIVE__PROP);
		out.value(isActive());
		out.name(CALLS__PROP);
		out.value(getCalls());
		out.name(VOTES__PROP);
		out.value(getVotes());
		out.name(RATING_LEGITIMATE__PROP);
		out.value(getRatingLegitimate());
		out.name(RATING_PING__PROP);
		out.value(getRatingPing());
		out.name(RATING_POLL__PROP);
		out.value(getRatingPoll());
		out.name(RATING_ADVERTISING__PROP);
		out.value(getRatingAdvertising());
		out.name(RATING_GAMBLE__PROP);
		out.value(getRatingGamble());
		out.name(RATING_FRAUD__PROP);
		out.value(getRatingFraud());
		out.name(SEARCHES__PROP);
		out.value(getSearches());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE__PROP: setPhone(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ACTIVE__PROP: setActive(in.nextBoolean()); break;
			case CALLS__PROP: setCalls(in.nextInt()); break;
			case VOTES__PROP: setVotes(in.nextInt()); break;
			case RATING_LEGITIMATE__PROP: setRatingLegitimate(in.nextInt()); break;
			case RATING_PING__PROP: setRatingPing(in.nextInt()); break;
			case RATING_POLL__PROP: setRatingPoll(in.nextInt()); break;
			case RATING_ADVERTISING__PROP: setRatingAdvertising(in.nextInt()); break;
			case RATING_GAMBLE__PROP: setRatingGamble(in.nextInt()); break;
			case RATING_FRAUD__PROP: setRatingFraud(in.nextInt()); break;
			case SEARCHES__PROP: setSearches(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.db.model.AbstractNumberInfo} type. */
	public static final String ABSTRACT_NUMBER_INFO__XML_ELEMENT = "abstract-number-info";

	/** XML attribute or element name of a {@link #getPhone} property. */
	private static final String PHONE__XML_ATTR = "phone";

	/** XML attribute or element name of a {@link #isActive} property. */
	private static final String ACTIVE__XML_ATTR = "active";

	/** XML attribute or element name of a {@link #getCalls} property. */
	private static final String CALLS__XML_ATTR = "calls";

	/** XML attribute or element name of a {@link #getVotes} property. */
	private static final String VOTES__XML_ATTR = "votes";

	/** XML attribute or element name of a {@link #getRatingLegitimate} property. */
	private static final String RATING_LEGITIMATE__XML_ATTR = "rating-legitimate";

	/** XML attribute or element name of a {@link #getRatingPing} property. */
	private static final String RATING_PING__XML_ATTR = "rating-ping";

	/** XML attribute or element name of a {@link #getRatingPoll} property. */
	private static final String RATING_POLL__XML_ATTR = "rating-poll";

	/** XML attribute or element name of a {@link #getRatingAdvertising} property. */
	private static final String RATING_ADVERTISING__XML_ATTR = "rating-advertising";

	/** XML attribute or element name of a {@link #getRatingGamble} property. */
	private static final String RATING_GAMBLE__XML_ATTR = "rating-gamble";

	/** XML attribute or element name of a {@link #getRatingFraud} property. */
	private static final String RATING_FRAUD__XML_ATTR = "rating-fraud";

	/** XML attribute or element name of a {@link #getSearches} property. */
	private static final String SEARCHES__XML_ATTR = "searches";

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(PHONE__XML_ATTR, getPhone());
		out.writeAttribute(ACTIVE__XML_ATTR, Boolean.toString(isActive()));
		out.writeAttribute(CALLS__XML_ATTR, Integer.toString(getCalls()));
		out.writeAttribute(VOTES__XML_ATTR, Integer.toString(getVotes()));
		out.writeAttribute(RATING_LEGITIMATE__XML_ATTR, Integer.toString(getRatingLegitimate()));
		out.writeAttribute(RATING_PING__XML_ATTR, Integer.toString(getRatingPing()));
		out.writeAttribute(RATING_POLL__XML_ATTR, Integer.toString(getRatingPoll()));
		out.writeAttribute(RATING_ADVERTISING__XML_ATTR, Integer.toString(getRatingAdvertising()));
		out.writeAttribute(RATING_GAMBLE__XML_ATTR, Integer.toString(getRatingGamble()));
		out.writeAttribute(RATING_FRAUD__XML_ATTR, Integer.toString(getRatingFraud()));
		out.writeAttribute(SEARCHES__XML_ATTR, Integer.toString(getSearches()));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.db.model.AbstractNumberInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AbstractNumberInfo readAbstractNumberInfo_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		switch (in.getLocalName()) {
			case NumberInfo.NUMBER_INFO__XML_ELEMENT: {
				return de.haumacher.phoneblock.db.model.NumberInfo.readNumberInfo_XmlContent(in);
			}

			case NumberHistory.NUMBER_HISTORY__XML_ELEMENT: {
				return de.haumacher.phoneblock.db.model.NumberHistory.readNumberHistory_XmlContent(in);
			}

			default: {
				internalSkipUntilMatchingEndElement(in);
				return null;
			}
		}
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
			case ACTIVE__XML_ATTR: {
				setActive(Boolean.parseBoolean(value));
				break;
			}
			case CALLS__XML_ATTR: {
				setCalls(Integer.parseInt(value));
				break;
			}
			case VOTES__XML_ATTR: {
				setVotes(Integer.parseInt(value));
				break;
			}
			case RATING_LEGITIMATE__XML_ATTR: {
				setRatingLegitimate(Integer.parseInt(value));
				break;
			}
			case RATING_PING__XML_ATTR: {
				setRatingPing(Integer.parseInt(value));
				break;
			}
			case RATING_POLL__XML_ATTR: {
				setRatingPoll(Integer.parseInt(value));
				break;
			}
			case RATING_ADVERTISING__XML_ATTR: {
				setRatingAdvertising(Integer.parseInt(value));
				break;
			}
			case RATING_GAMBLE__XML_ATTR: {
				setRatingGamble(Integer.parseInt(value));
				break;
			}
			case RATING_FRAUD__XML_ATTR: {
				setRatingFraud(Integer.parseInt(value));
				break;
			}
			case SEARCHES__XML_ATTR: {
				setSearches(Integer.parseInt(value));
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
			case ACTIVE__XML_ATTR: {
				setActive(Boolean.parseBoolean(in.getElementText()));
				break;
			}
			case CALLS__XML_ATTR: {
				setCalls(Integer.parseInt(in.getElementText()));
				break;
			}
			case VOTES__XML_ATTR: {
				setVotes(Integer.parseInt(in.getElementText()));
				break;
			}
			case RATING_LEGITIMATE__XML_ATTR: {
				setRatingLegitimate(Integer.parseInt(in.getElementText()));
				break;
			}
			case RATING_PING__XML_ATTR: {
				setRatingPing(Integer.parseInt(in.getElementText()));
				break;
			}
			case RATING_POLL__XML_ATTR: {
				setRatingPoll(Integer.parseInt(in.getElementText()));
				break;
			}
			case RATING_ADVERTISING__XML_ATTR: {
				setRatingAdvertising(Integer.parseInt(in.getElementText()));
				break;
			}
			case RATING_GAMBLE__XML_ATTR: {
				setRatingGamble(Integer.parseInt(in.getElementText()));
				break;
			}
			case RATING_FRAUD__XML_ATTR: {
				setRatingFraud(Integer.parseInt(in.getElementText()));
				break;
			}
			case SEARCHES__XML_ATTR: {
				setSearches(Integer.parseInt(in.getElementText()));
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

	/** Creates a new {@link AbstractNumberInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AbstractNumberInfo readAbstractNumberInfo(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.db.model.AbstractNumberInfo.readAbstractNumberInfo_XmlContent(in);
	}

	/** Accepts the given visitor. */
	public abstract <R,A,E extends Throwable> R visit(Visitor<R,A,E> v, A arg) throws E;

}
