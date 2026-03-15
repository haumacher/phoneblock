package de.haumacher.mailcheck.provider.rapidapi.model;

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
public class RapidAPIResult extends de.haumacher.msgbuf.data.AbstractDataObject {

	/**
	 * Creates a {@link de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult} instance.
	 */
	public static de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult create() {
		return new de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult();
	}

	/** Identifier for the {@link de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult} type in JSON format. */
	public static final String RAPID_APIRESULT__TYPE = "RapidAPIResult";

	/** @see #isValid() */
	private static final String VALID__PROP = "valid";

	/** @see #isBlock() */
	private static final String BLOCK__PROP = "block";

	/** @see #isDisposable() */
	private static final String DISPOSABLE__PROP = "disposable";

	/** @see #isForwarder() */
	private static final String FORWARDER__PROP = "email_forwarder";

	/** @see #getDomainName() */
	private static final String DOMAIN_NAME__PROP = "domain";

	/** @see #getMxHost() */
	private static final String MX_HOST__PROP = "mx_host";

	/** @see #getMxIP() */
	private static final String MX_IP__PROP = "mx_ip";

	/** @see #getLastChanged() */
	private static final String LAST_CHANGED__PROP = "last_changed_at";

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
	 * @see de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult#create()
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
	public de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult setValid(boolean value) {
		internalSetValid(value);
		return this;
	}

	/** Internal setter for {@link #isValid()} without chain call utility. */
	protected final void internalSetValid(boolean value) {
		_valid = value;
	}

	public final boolean isBlock() {
		return _block;
	}

	/**
	 * @see #isBlock()
	 */
	public de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult setBlock(boolean value) {
		internalSetBlock(value);
		return this;
	}

	/** Internal setter for {@link #isBlock()} without chain call utility. */
	protected final void internalSetBlock(boolean value) {
		_block = value;
	}

	public final boolean isDisposable() {
		return _disposable;
	}

	/**
	 * @see #isDisposable()
	 */
	public de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult setDisposable(boolean value) {
		internalSetDisposable(value);
		return this;
	}

	/** Internal setter for {@link #isDisposable()} without chain call utility. */
	protected final void internalSetDisposable(boolean value) {
		_disposable = value;
	}

	public final boolean isForwarder() {
		return _forwarder;
	}

	/**
	 * @see #isForwarder()
	 */
	public de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult setForwarder(boolean value) {
		internalSetForwarder(value);
		return this;
	}

	/** Internal setter for {@link #isForwarder()} without chain call utility. */
	protected final void internalSetForwarder(boolean value) {
		_forwarder = value;
	}

	public final String getDomainName() {
		return _domainName;
	}

	/**
	 * @see #getDomainName()
	 */
	public de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult setDomainName(String value) {
		internalSetDomainName(value);
		return this;
	}

	/** Internal setter for {@link #getDomainName()} without chain call utility. */
	protected final void internalSetDomainName(String value) {
		_domainName = value;
	}

	public final String getMxHost() {
		return _mxHost;
	}

	/**
	 * @see #getMxHost()
	 */
	public de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult setMxHost(String value) {
		internalSetMxHost(value);
		return this;
	}

	/** Internal setter for {@link #getMxHost()} without chain call utility. */
	protected final void internalSetMxHost(String value) {
		_mxHost = value;
	}

	public final String getMxIP() {
		return _mxIP;
	}

	/**
	 * @see #getMxIP()
	 */
	public de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult setMxIP(String value) {
		internalSetMxIP(value);
		return this;
	}

	/** Internal setter for {@link #getMxIP()} without chain call utility. */
	protected final void internalSetMxIP(String value) {
		_mxIP = value;
	}

	public final String getLastChanged() {
		return _lastChanged;
	}

	/**
	 * @see #getLastChanged()
	 */
	public de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult setLastChanged(String value) {
		internalSetLastChanged(value);
		return this;
	}

	/** Internal setter for {@link #getLastChanged()} without chain call utility. */
	protected final void internalSetLastChanged(String value) {
		_lastChanged = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult readRapidAPIResult(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult result = new de.haumacher.mailcheck.provider.rapidapi.model.RapidAPIResult();
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
