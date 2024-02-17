package de.haumacher.phoneblock.mail.check.model;

/**
 * Check result from <code>https://mailcheck.p.rapidapi.com</code>.
 *
 * <pre>
 * 	{
	 * 	  "valid": true,
	 * 	  "block": true,
	 * 	  "disposable": true,
	 * 	  "email_forwarder": false,
	 * 	  "domain": "laymro.com",
	 * 	  "text": "Disposable / temporary domain",
	 * 	  "reason": "Heuristics x1",
	 * 	  "risk": 91,
	 * 	  "mx_host": "mail.laymro.com",
	 * 	  "possible_typo": [],
	 * 	  "mx_ip": "167.172.15.120",
	 * 	  "mx_info": "Using MX pointer mail.laymro.com from DNS with priority: 10",
	 * 	  "last_changed_at": "2024-02-07T17:09:29+01:00"
 * 	} 
 * </pre>
 */
public class RapidAPIResult extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.mail.check.model.RapidAPIResult} instance.
	 */
	public static de.haumacher.phoneblock.mail.check.model.RapidAPIResult create() {
		return new de.haumacher.phoneblock.mail.check.model.RapidAPIResult();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.mail.check.model.RapidAPIResult} type in JSON format. */
	public static final String RAPID_APIRESULT__TYPE = "RapidAPIResult";

	/** @see #isValid() */
	public static final String VALID__PROP = "valid";

	/** @see #isBlock() */
	public static final String BLOCK__PROP = "block";

	/** @see #isDisposable() */
	public static final String DISPOSABLE__PROP = "disposable";

	/** @see #isForwarder() */
	public static final String FORWARDER__PROP = "email_forwarder";

	/** @see #getDomainName() */
	public static final String DOMAIN_NAME__PROP = "domain";

	/** @see #getMxHost() */
	public static final String MX_HOST__PROP = "mx_host";

	/** @see #getMxIP() */
	public static final String MX_IP__PROP = "mx_ip";

	/** @see #getLastChanged() */
	public static final String LAST_CHANGED__PROP = "last_changed_at";

	private boolean _valid = false;

	private boolean _block = false;

	private boolean _disposable = false;

	private boolean _forwarder = false;

	private String _domainName = "";

	private String _mxHost = "";

	private String _mxIP = "";

	private String _lastChanged = "";

	/**
	 * Creates a {@link RapidAPIResult} instance.
	 *
	 * @see de.haumacher.phoneblock.mail.check.model.RapidAPIResult#create()
	 */
	protected RapidAPIResult() {
		super();
	}

	public final boolean isValid() {
		return _valid;
	}

	/**
	 * @see #isValid()
	 */
	public de.haumacher.phoneblock.mail.check.model.RapidAPIResult setValid(boolean value) {
		internalSetValid(value);
		return this;
	}

	/** Internal setter for {@link #isValid()} without chain call utility. */
	protected final void internalSetValid(boolean value) {
		_listener.beforeSet(this, VALID__PROP, value);
		_valid = value;
	}

	public final boolean isBlock() {
		return _block;
	}

	/**
	 * @see #isBlock()
	 */
	public de.haumacher.phoneblock.mail.check.model.RapidAPIResult setBlock(boolean value) {
		internalSetBlock(value);
		return this;
	}

	/** Internal setter for {@link #isBlock()} without chain call utility. */
	protected final void internalSetBlock(boolean value) {
		_listener.beforeSet(this, BLOCK__PROP, value);
		_block = value;
	}

	public final boolean isDisposable() {
		return _disposable;
	}

	/**
	 * @see #isDisposable()
	 */
	public de.haumacher.phoneblock.mail.check.model.RapidAPIResult setDisposable(boolean value) {
		internalSetDisposable(value);
		return this;
	}

	/** Internal setter for {@link #isDisposable()} without chain call utility. */
	protected final void internalSetDisposable(boolean value) {
		_listener.beforeSet(this, DISPOSABLE__PROP, value);
		_disposable = value;
	}

	public final boolean isForwarder() {
		return _forwarder;
	}

	/**
	 * @see #isForwarder()
	 */
	public de.haumacher.phoneblock.mail.check.model.RapidAPIResult setForwarder(boolean value) {
		internalSetForwarder(value);
		return this;
	}

	/** Internal setter for {@link #isForwarder()} without chain call utility. */
	protected final void internalSetForwarder(boolean value) {
		_listener.beforeSet(this, FORWARDER__PROP, value);
		_forwarder = value;
	}

	public final String getDomainName() {
		return _domainName;
	}

	/**
	 * @see #getDomainName()
	 */
	public de.haumacher.phoneblock.mail.check.model.RapidAPIResult setDomainName(String value) {
		internalSetDomainName(value);
		return this;
	}

	/** Internal setter for {@link #getDomainName()} without chain call utility. */
	protected final void internalSetDomainName(String value) {
		_listener.beforeSet(this, DOMAIN_NAME__PROP, value);
		_domainName = value;
	}

	public final String getMxHost() {
		return _mxHost;
	}

	/**
	 * @see #getMxHost()
	 */
	public de.haumacher.phoneblock.mail.check.model.RapidAPIResult setMxHost(String value) {
		internalSetMxHost(value);
		return this;
	}

	/** Internal setter for {@link #getMxHost()} without chain call utility. */
	protected final void internalSetMxHost(String value) {
		_listener.beforeSet(this, MX_HOST__PROP, value);
		_mxHost = value;
	}

	public final String getMxIP() {
		return _mxIP;
	}

	/**
	 * @see #getMxIP()
	 */
	public de.haumacher.phoneblock.mail.check.model.RapidAPIResult setMxIP(String value) {
		internalSetMxIP(value);
		return this;
	}

	/** Internal setter for {@link #getMxIP()} without chain call utility. */
	protected final void internalSetMxIP(String value) {
		_listener.beforeSet(this, MX_IP__PROP, value);
		_mxIP = value;
	}

	public final String getLastChanged() {
		return _lastChanged;
	}

	/**
	 * @see #getLastChanged()
	 */
	public de.haumacher.phoneblock.mail.check.model.RapidAPIResult setLastChanged(String value) {
		internalSetLastChanged(value);
		return this;
	}

	/** Internal setter for {@link #getLastChanged()} without chain call utility. */
	protected final void internalSetLastChanged(String value) {
		_listener.beforeSet(this, LAST_CHANGED__PROP, value);
		_lastChanged = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.mail.check.model.RapidAPIResult registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.mail.check.model.RapidAPIResult unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return RAPID_APIRESULT__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			VALID__PROP, 
			BLOCK__PROP, 
			DISPOSABLE__PROP, 
			FORWARDER__PROP, 
			DOMAIN_NAME__PROP, 
			MX_HOST__PROP, 
			MX_IP__PROP, 
			LAST_CHANGED__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case VALID__PROP: return isValid();
			case BLOCK__PROP: return isBlock();
			case DISPOSABLE__PROP: return isDisposable();
			case FORWARDER__PROP: return isForwarder();
			case DOMAIN_NAME__PROP: return getDomainName();
			case MX_HOST__PROP: return getMxHost();
			case MX_IP__PROP: return getMxIP();
			case LAST_CHANGED__PROP: return getLastChanged();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case VALID__PROP: internalSetValid((boolean) value); break;
			case BLOCK__PROP: internalSetBlock((boolean) value); break;
			case DISPOSABLE__PROP: internalSetDisposable((boolean) value); break;
			case FORWARDER__PROP: internalSetForwarder((boolean) value); break;
			case DOMAIN_NAME__PROP: internalSetDomainName((String) value); break;
			case MX_HOST__PROP: internalSetMxHost((String) value); break;
			case MX_IP__PROP: internalSetMxIP((String) value); break;
			case LAST_CHANGED__PROP: internalSetLastChanged((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.mail.check.model.RapidAPIResult readRapidAPIResult(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.mail.check.model.RapidAPIResult result = new de.haumacher.phoneblock.mail.check.model.RapidAPIResult();
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
		out.name(VALID__PROP);
		out.value(isValid());
		out.name(BLOCK__PROP);
		out.value(isBlock());
		out.name(DISPOSABLE__PROP);
		out.value(isDisposable());
		out.name(FORWARDER__PROP);
		out.value(isForwarder());
		out.name(DOMAIN_NAME__PROP);
		out.value(getDomainName());
		out.name(MX_HOST__PROP);
		out.value(getMxHost());
		out.name(MX_IP__PROP);
		out.value(getMxIP());
		out.name(LAST_CHANGED__PROP);
		out.value(getLastChanged());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case VALID__PROP: setValid(in.nextBoolean()); break;
			case BLOCK__PROP: setBlock(in.nextBoolean()); break;
			case DISPOSABLE__PROP: setDisposable(in.nextBoolean()); break;
			case FORWARDER__PROP: setForwarder(in.nextBoolean()); break;
			case DOMAIN_NAME__PROP: setDomainName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MX_HOST__PROP: setMxHost(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MX_IP__PROP: setMxIP(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LAST_CHANGED__PROP: setLastChanged(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

}
