package de.haumacher.phoneblock.mail.check.model;

/**
 * Provider-independent result of a disposable e-mail domain check.
 *
 * <p>
 * Cached in the DOMAIN_CHECK database table so that repeated lookups for the
 * same domain do not require another provider call.
 * </p>
 *
 * @see de.haumacher.phoneblock.mail.check.DomainCheckProvider
 */
public class DomainCheck extends de.haumacher.msgbuf.data.AbstractDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.mail.check.model.DomainCheck} instance.
	 */
	public static de.haumacher.phoneblock.mail.check.model.DomainCheck create() {
		return new de.haumacher.phoneblock.mail.check.model.DomainCheck();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.mail.check.model.DomainCheck} type in JSON format. */
	public static final String DOMAIN_CHECK__TYPE = "DomainCheck";

	/** @see #getDomainName() */
	private static final String DOMAIN_NAME__PROP = "domainName";

	/** @see #isDisposable() */
	private static final String DISPOSABLE__PROP = "disposable";

	/** @see #getLastChanged() */
	private static final String LAST_CHANGED__PROP = "lastChanged";

	/** @see #getSourceSystem() */
	private static final String SOURCE_SYSTEM__PROP = "sourceSystem";

	/** @see #getMxHost() */
	private static final String MX_HOST__PROP = "mxHost";

	/** @see #getMxIP() */
	private static final String MX_IP__PROP = "mxIP";

	private String _domainName = "";

	private boolean _disposable = false;

	private long _lastChanged = 0L;

	private String _sourceSystem = "";

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

	/**
	 * The fully qualified domain name that was checked (e.g. "laymro.com").
	 */
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
		_domainName = value;
	}

	/**
	 * Whether this domain is known to provide disposable/temporary e-mail addresses.
	 */
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
		_disposable = value;
	}

	/**
	 * Timestamp (Unix milliseconds) when the domain status was last changed at the provider.
	 */
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
		_lastChanged = value;
	}

	/**
	 * Identifier of the provider that produced this result (e.g. "rapidapi").
	 */
	public final String getSourceSystem() {
		return _sourceSystem;
	}

	/**
	 * @see #getSourceSystem()
	 */
	public de.haumacher.phoneblock.mail.check.model.DomainCheck setSourceSystem(String value) {
		internalSetSourceSystem(value);
		return this;
	}

	/** Internal setter for {@link #getSourceSystem()} without chain call utility. */
	protected final void internalSetSourceSystem(String value) {
		_sourceSystem = value;
	}

	/**
	 * The MX hostname resolved for this domain, or {@code null} if unavailable.
	 */
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
		_mxHost = value;
	}

	/**
	 * Checks, whether {@link #getMxHost()} has a value.
	 */
	public final boolean hasMxHost() {
		return _mxHost != null;
	}

	/**
	 * The IP address of the MX host, or {@code null} if unavailable.
	 */
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
		_mxIP = value;
	}

	/**
	 * Checks, whether {@link #getMxIP()} has a value.
	 */
	public final boolean hasMxIP() {
		return _mxIP != null;
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
			case SOURCE_SYSTEM__PROP: setSourceSystem(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MX_HOST__PROP: setMxHost(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MX_IP__PROP: setMxIP(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

}
