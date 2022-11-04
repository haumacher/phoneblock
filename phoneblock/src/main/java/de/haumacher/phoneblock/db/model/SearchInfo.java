package de.haumacher.phoneblock.db.model;

/**
 * Info about how often a number was searched.
 */
public class SearchInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link SearchInfo} instance.
	 */
	public static SearchInfo create() {
		return new de.haumacher.phoneblock.db.model.SearchInfo();
	}

	/** Identifier for the {@link SearchInfo} type in JSON format. */
	public static final String SEARCH_INFO__TYPE = "SearchInfo";

	/** @see #getRevision() */
	public static final String REVISION__PROP = "revision";

	/** @see #getPhone() */
	public static final String PHONE__PROP = "phone";

	/** @see #getCount() */
	public static final String COUNT__PROP = "count";

	/** @see #getTotal() */
	public static final String TOTAL__PROP = "total";

	/** @see #getLastSearch() */
	public static final String LAST_SEARCH__PROP = "lastSearch";

	private int _revision = 0;

	private String _phone = "";

	private int _count = 0;

	private int _total = 0;

	private long _lastSearch = 0L;

	/**
	 * Creates a {@link SearchInfo} instance.
	 *
	 * @see SearchInfo#create()
	 */
	protected SearchInfo() {
		super();
	}

	/**
	 * The time slot, this information is about.
	 */
	public final int getRevision() {
		return _revision;
	}

	/**
	 * @see #getRevision()
	 */
	public SearchInfo setRevision(int value) {
		internalSetRevision(value);
		return this;
	}

	/** Internal setter for {@link #getRevision()} without chain call utility. */
	protected final void internalSetRevision(int value) {
		_listener.beforeSet(this, REVISION__PROP, value);
		_revision = value;
	}

	/**
	 * The phone number
	 */
	public final String getPhone() {
		return _phone;
	}

	/**
	 * @see #getPhone()
	 */
	public SearchInfo setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	/** Internal setter for {@link #getPhone()} without chain call utility. */
	protected final void internalSetPhone(String value) {
		_listener.beforeSet(this, PHONE__PROP, value);
		_phone = value;
	}

	/**
	 * The number of search requests in the {@link #getRevision() time slot}.
	 */
	public final int getCount() {
		return _count;
	}

	/**
	 * @see #getCount()
	 */
	public SearchInfo setCount(int value) {
		internalSetCount(value);
		return this;
	}

	/** Internal setter for {@link #getCount()} without chain call utility. */
	protected final void internalSetCount(int value) {
		_listener.beforeSet(this, COUNT__PROP, value);
		_count = value;
	}

	/**
	 * Some other number of serch requests (context dependent).
	 */
	public final int getTotal() {
		return _total;
	}

	/**
	 * @see #getTotal()
	 */
	public SearchInfo setTotal(int value) {
		internalSetTotal(value);
		return this;
	}

	/** Internal setter for {@link #getTotal()} without chain call utility. */
	protected final void internalSetTotal(int value) {
		_listener.beforeSet(this, TOTAL__PROP, value);
		_total = value;
	}

	/**
	 * When the last search request was performed for the {@link #getPhone() number} in the {@link #getRevision() time slot}.
	 */
	public final long getLastSearch() {
		return _lastSearch;
	}

	/**
	 * @see #getLastSearch()
	 */
	public SearchInfo setLastSearch(long value) {
		internalSetLastSearch(value);
		return this;
	}

	/** Internal setter for {@link #getLastSearch()} without chain call utility. */
	protected final void internalSetLastSearch(long value) {
		_listener.beforeSet(this, LAST_SEARCH__PROP, value);
		_lastSearch = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public SearchInfo registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public SearchInfo unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return SEARCH_INFO__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			REVISION__PROP, 
			PHONE__PROP, 
			COUNT__PROP, 
			TOTAL__PROP, 
			LAST_SEARCH__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case REVISION__PROP: return getRevision();
			case PHONE__PROP: return getPhone();
			case COUNT__PROP: return getCount();
			case TOTAL__PROP: return getTotal();
			case LAST_SEARCH__PROP: return getLastSearch();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case REVISION__PROP: internalSetRevision((int) value); break;
			case PHONE__PROP: internalSetPhone((String) value); break;
			case COUNT__PROP: internalSetCount((int) value); break;
			case TOTAL__PROP: internalSetTotal((int) value); break;
			case LAST_SEARCH__PROP: internalSetLastSearch((long) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static SearchInfo readSearchInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.SearchInfo result = new de.haumacher.phoneblock.db.model.SearchInfo();
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
		out.name(REVISION__PROP);
		out.value(getRevision());
		out.name(PHONE__PROP);
		out.value(getPhone());
		out.name(COUNT__PROP);
		out.value(getCount());
		out.name(TOTAL__PROP);
		out.value(getTotal());
		out.name(LAST_SEARCH__PROP);
		out.value(getLastSearch());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case REVISION__PROP: setRevision(in.nextInt()); break;
			case PHONE__PROP: setPhone(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case COUNT__PROP: setCount(in.nextInt()); break;
			case TOTAL__PROP: setTotal(in.nextInt()); break;
			case LAST_SEARCH__PROP: setLastSearch(in.nextLong()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link SearchInfo} type. */
	public static final String SEARCH_INFO__XML_ELEMENT = "search-info";

	/** XML attribute or element name of a {@link #getRevision} property. */
	private static final String REVISION__XML_ATTR = "revision";

	/** XML attribute or element name of a {@link #getPhone} property. */
	private static final String PHONE__XML_ATTR = "phone";

	/** XML attribute or element name of a {@link #getCount} property. */
	private static final String COUNT__XML_ATTR = "count";

	/** XML attribute or element name of a {@link #getTotal} property. */
	private static final String TOTAL__XML_ATTR = "total";

	/** XML attribute or element name of a {@link #getLastSearch} property. */
	private static final String LAST_SEARCH__XML_ATTR = "last-search";

	@Override
	public String getXmlTagName() {
		return SEARCH_INFO__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(REVISION__XML_ATTR, Integer.toString(getRevision()));
		out.writeAttribute(PHONE__XML_ATTR, getPhone());
		out.writeAttribute(COUNT__XML_ATTR, Integer.toString(getCount()));
		out.writeAttribute(TOTAL__XML_ATTR, Integer.toString(getTotal()));
		out.writeAttribute(LAST_SEARCH__XML_ATTR, Long.toString(getLastSearch()));
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
	}

	/** Creates a new {@link SearchInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static SearchInfo readSearchInfo_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		SearchInfo result = new SearchInfo();
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
			case REVISION__XML_ATTR: {
				setRevision(Integer.parseInt(value));
				break;
			}
			case PHONE__XML_ATTR: {
				setPhone(value);
				break;
			}
			case COUNT__XML_ATTR: {
				setCount(Integer.parseInt(value));
				break;
			}
			case TOTAL__XML_ATTR: {
				setTotal(Integer.parseInt(value));
				break;
			}
			case LAST_SEARCH__XML_ATTR: {
				setLastSearch(Long.parseLong(value));
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
			case REVISION__XML_ATTR: {
				setRevision(Integer.parseInt(in.getElementText()));
				break;
			}
			case PHONE__XML_ATTR: {
				setPhone(in.getElementText());
				break;
			}
			case COUNT__XML_ATTR: {
				setCount(Integer.parseInt(in.getElementText()));
				break;
			}
			case TOTAL__XML_ATTR: {
				setTotal(Integer.parseInt(in.getElementText()));
				break;
			}
			case LAST_SEARCH__XML_ATTR: {
				setLastSearch(Long.parseLong(in.getElementText()));
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

	/** Creates a new {@link SearchInfo} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static SearchInfo readSearchInfo(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.db.model.SearchInfo.readSearchInfo_XmlContent(in);
	}

}
