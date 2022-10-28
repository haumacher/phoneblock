package de.haumacher.phoneblock.db.model;

/**
 * Info about how often a number was searched.
 */
public class SearchInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable {

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

}
