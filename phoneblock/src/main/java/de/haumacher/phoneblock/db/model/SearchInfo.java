package de.haumacher.phoneblock.db.model;

/**
 * Info about how often a number was searched.
 */
public class SearchInfo implements de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link SearchInfo} instance.
	 */
	public static SearchInfo create() {
		return new de.haumacher.phoneblock.db.model.SearchInfo();
	}

	/** Identifier for the {@link SearchInfo} type in JSON format. */
	public static final String SEARCH_INFO__TYPE = "SearchInfo";

	/** @see #getRevision() */
	public static final String REVISION = "revision";

	/** @see #getPhone() */
	public static final String PHONE = "phone";

	/** @see #getCount() */
	public static final String COUNT = "count";

	/** @see #getTotal() */
	public static final String TOTAL = "total";

	/** @see #getLastSearch() */
	public static final String LAST_SEARCH = "lastSearch";

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
		_listener.beforeSet(this, REVISION, value);
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
		_listener.beforeSet(this, PHONE, value);
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
		_listener.beforeSet(this, COUNT, value);
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
		_listener.beforeSet(this, TOTAL, value);
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
		_listener.beforeSet(this, LAST_SEARCH, value);
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
			REVISION, 
			PHONE, 
			COUNT, 
			TOTAL, 
			LAST_SEARCH));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case REVISION: return getRevision();
			case PHONE: return getPhone();
			case COUNT: return getCount();
			case TOTAL: return getTotal();
			case LAST_SEARCH: return getLastSearch();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case REVISION: internalSetRevision((int) value); break;
			case PHONE: internalSetPhone((String) value); break;
			case COUNT: internalSetCount((int) value); break;
			case TOTAL: internalSetTotal((int) value); break;
			case LAST_SEARCH: internalSetLastSearch((long) value); break;
		}
	}

}
