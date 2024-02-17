package de.haumacher.phoneblock.mail.check.model;

public class DomainCheck extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.mail.check.model.DomainCheck} instance.
	 */
	public static de.haumacher.phoneblock.mail.check.model.DomainCheck create() {
		return new de.haumacher.phoneblock.mail.check.model.DomainCheck();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.mail.check.model.DomainCheck} type in JSON format. */
	public static final String DOMAIN_CHECK__TYPE = "DomainCheck";

	/** @see #getDomainName() */
	public static final String DOMAIN_NAME__PROP = "domainName";

	/** @see #isDisposable() */
	public static final String DISPOSABLE__PROP = "disposable";

	/** @see #getLastChanged() */
	public static final String LAST_CHANGED__PROP = "lastChanged";

	/** @see #getSourceSystem() */
	public static final String SOURCE_SYSTEM__PROP = "sourceSystem";

	/** @see #getMxHost() */
	public static final String MX_HOST__PROP = "mxHost";

	/** @see #getMxIP() */
	public static final String MX_IP__PROP = "mxIP";

	private String _domainName = "";

	private boolean _disposable = false;

	private long _lastChanged = 0L;

	private int _sourceSystem = 0;

	private String _mxHost = null;

	private String _mxIP = null;

	/**
	 * Creates a {@link DomainCheck} instance.
	 *
	 * @see de.haumacher.phoneblock.mail.check.model.DomainCheck#create()
	 */
	protected DomainCheck() {
		super();
	}

	public final String getDomainName() {
		return _domainName;
	}

	/**
	 * @see #getDomainName()
	 */
	public de.haumacher.phoneblock.mail.check.model.DomainCheck setDomainName(String value) {
		internalSetDomainName(value);
		return this;
	}

	/** Internal setter for {@link #getDomainName()} without chain call utility. */
	protected final void internalSetDomainName(String value) {
		_listener.beforeSet(this, DOMAIN_NAME__PROP, value);
		_domainName = value;
	}

	public final boolean isDisposable() {
		return _disposable;
	}

	/**
	 * @see #isDisposable()
	 */
	public de.haumacher.phoneblock.mail.check.model.DomainCheck setDisposable(boolean value) {
		internalSetDisposable(value);
		return this;
	}

	/** Internal setter for {@link #isDisposable()} without chain call utility. */
	protected final void internalSetDisposable(boolean value) {
		_listener.beforeSet(this, DISPOSABLE__PROP, value);
		_disposable = value;
	}

	public final long getLastChanged() {
		return _lastChanged;
	}

	/**
	 * @see #getLastChanged()
	 */
	public de.haumacher.phoneblock.mail.check.model.DomainCheck setLastChanged(long value) {
		internalSetLastChanged(value);
		return this;
	}

	/** Internal setter for {@link #getLastChanged()} without chain call utility. */
	protected final void internalSetLastChanged(long value) {
		_listener.beforeSet(this, LAST_CHANGED__PROP, value);
		_lastChanged = value;
	}

	public final int getSourceSystem() {
		return _sourceSystem;
	}

	/**
	 * @see #getSourceSystem()
	 */
	public de.haumacher.phoneblock.mail.check.model.DomainCheck setSourceSystem(int value) {
		internalSetSourceSystem(value);
		return this;
	}

	/** Internal setter for {@link #getSourceSystem()} without chain call utility. */
	protected final void internalSetSourceSystem(int value) {
		_listener.beforeSet(this, SOURCE_SYSTEM__PROP, value);
		_sourceSystem = value;
	}

	public final String getMxHost() {
		return _mxHost;
	}

	/**
	 * @see #getMxHost()
	 */
	public de.haumacher.phoneblock.mail.check.model.DomainCheck setMxHost(String value) {
		internalSetMxHost(value);
		return this;
	}

	/** Internal setter for {@link #getMxHost()} without chain call utility. */
	protected final void internalSetMxHost(String value) {
		_listener.beforeSet(this, MX_HOST__PROP, value);
		_mxHost = value;
	}

	/**
	 * Checks, whether {@link #getMxHost()} has a value.
	 */
	public final boolean hasMxHost() {
		return _mxHost != null;
	}

	public final String getMxIP() {
		return _mxIP;
	}

	/**
	 * @see #getMxIP()
	 */
	public de.haumacher.phoneblock.mail.check.model.DomainCheck setMxIP(String value) {
		internalSetMxIP(value);
		return this;
	}

	/** Internal setter for {@link #getMxIP()} without chain call utility. */
	protected final void internalSetMxIP(String value) {
		_listener.beforeSet(this, MX_IP__PROP, value);
		_mxIP = value;
	}

	/**
	 * Checks, whether {@link #getMxIP()} has a value.
	 */
	public final boolean hasMxIP() {
		return _mxIP != null;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.mail.check.model.DomainCheck registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.mail.check.model.DomainCheck unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return DOMAIN_CHECK__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			DOMAIN_NAME__PROP, 
			DISPOSABLE__PROP, 
			LAST_CHANGED__PROP, 
			SOURCE_SYSTEM__PROP, 
			MX_HOST__PROP, 
			MX_IP__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case DOMAIN_NAME__PROP: return getDomainName();
			case DISPOSABLE__PROP: return isDisposable();
			case LAST_CHANGED__PROP: return getLastChanged();
			case SOURCE_SYSTEM__PROP: return getSourceSystem();
			case MX_HOST__PROP: return getMxHost();
			case MX_IP__PROP: return getMxIP();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case DOMAIN_NAME__PROP: internalSetDomainName((String) value); break;
			case DISPOSABLE__PROP: internalSetDisposable((boolean) value); break;
			case LAST_CHANGED__PROP: internalSetLastChanged((long) value); break;
			case SOURCE_SYSTEM__PROP: internalSetSourceSystem((int) value); break;
			case MX_HOST__PROP: internalSetMxHost((String) value); break;
			case MX_IP__PROP: internalSetMxIP((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.mail.check.model.DomainCheck readDomainCheck(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.mail.check.model.DomainCheck result = new de.haumacher.phoneblock.mail.check.model.DomainCheck();
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
		out.name(DOMAIN_NAME__PROP);
		out.value(getDomainName());
		out.name(DISPOSABLE__PROP);
		out.value(isDisposable());
		out.name(LAST_CHANGED__PROP);
		out.value(getLastChanged());
		out.name(SOURCE_SYSTEM__PROP);
		out.value(getSourceSystem());
		if (hasMxHost()) {
			out.name(MX_HOST__PROP);
			out.value(getMxHost());
		}
		if (hasMxIP()) {
			out.name(MX_IP__PROP);
			out.value(getMxIP());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case DOMAIN_NAME__PROP: setDomainName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DISPOSABLE__PROP: setDisposable(in.nextBoolean()); break;
			case LAST_CHANGED__PROP: setLastChanged(in.nextLong()); break;
			case SOURCE_SYSTEM__PROP: setSourceSystem(in.nextInt()); break;
			case MX_HOST__PROP: setMxHost(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MX_IP__PROP: setMxIP(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

}
