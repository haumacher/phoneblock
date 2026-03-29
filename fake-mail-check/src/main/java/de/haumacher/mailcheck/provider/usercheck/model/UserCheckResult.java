package de.haumacher.mailcheck.provider.usercheck.model;

/**
 * Check result from <code>https://api.usercheck.com/domain/{domain}</code>.
 *
 * <pre>
 * 	{
	 * 	  "domain": "laymro.com",
	 * 	  "mx": true,
	 * 	  "disposable": true,
	 * 	  "public_domain": false
 * 	}
 * </pre>
 */
public class UserCheckResult extends de.haumacher.msgbuf.data.AbstractDataObject {

	/**
	 * Creates a {@link de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult} instance.
	 */
	public static de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult create() {
		return new de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult();
	}

	/** Identifier for the {@link de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult} type in JSON format. */
	public static final String USER_CHECK_RESULT__TYPE = "UserCheckResult";

	/** @see #getDomainName() */
	private static final String DOMAIN_NAME__PROP = "domain";

	/** @see #isDisposable() */
	private static final String DISPOSABLE__PROP = "disposable";

	/** @see #isMx() */
	private static final String MX__PROP = "mx";

	/** @see #isPublicDomain() */
	private static final String PUBLIC_DOMAIN__PROP = "public_domain";

	private String _domainName = "";

	private boolean _disposable = false;

	private boolean _mx = false;

	private boolean _publicDomain = false;

	/**
	 * Creates a {@link UserCheckResult} instance.
	 *
	 * @see de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult#create()
	 */
	protected UserCheckResult() {
		super();
	}

	public final String getDomainName() {
		return _domainName;
	}

	/**
	 * @see #getDomainName()
	 */
	public de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult setDomainName(String value) {
		internalSetDomainName(value);
		return this;
	}

	/** Internal setter for {@link #getDomainName()} without chain call utility. */
	protected final void internalSetDomainName(String value) {
		_domainName = value;
	}

	public final boolean isDisposable() {
		return _disposable;
	}

	/**
	 * @see #isDisposable()
	 */
	public de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult setDisposable(boolean value) {
		internalSetDisposable(value);
		return this;
	}

	/** Internal setter for {@link #isDisposable()} without chain call utility. */
	protected final void internalSetDisposable(boolean value) {
		_disposable = value;
	}

	public final boolean isMx() {
		return _mx;
	}

	/**
	 * @see #isMx()
	 */
	public de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult setMx(boolean value) {
		internalSetMx(value);
		return this;
	}

	/** Internal setter for {@link #isMx()} without chain call utility. */
	protected final void internalSetMx(boolean value) {
		_mx = value;
	}

	public final boolean isPublicDomain() {
		return _publicDomain;
	}

	/**
	 * @see #isPublicDomain()
	 */
	public de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult setPublicDomain(boolean value) {
		internalSetPublicDomain(value);
		return this;
	}

	/** Internal setter for {@link #isPublicDomain()} without chain call utility. */
	protected final void internalSetPublicDomain(boolean value) {
		_publicDomain = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult readUserCheckResult(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult result = new de.haumacher.mailcheck.provider.usercheck.model.UserCheckResult();
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
		out.name(MX__PROP);
		out.value(isMx());
		out.name(PUBLIC_DOMAIN__PROP);
		out.value(isPublicDomain());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case DOMAIN_NAME__PROP: setDomainName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DISPOSABLE__PROP: setDisposable(in.nextBoolean()); break;
			case MX__PROP: setMx(in.nextBoolean()); break;
			case PUBLIC_DOMAIN__PROP: setPublicDomain(in.nextBoolean()); break;
			default: super.readField(in, field);
		}
	}

}
