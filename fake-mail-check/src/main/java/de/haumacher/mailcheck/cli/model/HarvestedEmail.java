package de.haumacher.mailcheck.cli.model;

/**
 * A harvested e-mail address from a browser extension export.
 *
 * <pre>
 * {
	 *   "email": "user@gmail.com",
	 *   "type": "gmail",
	 *   "domain": "googlemail.com",
	 *   "source": "22do",
	 *   "originalEmail": "u.s.e.r+tag@googlemail.com"
 * }
 * </pre>
 */
public class HarvestedEmail extends de.haumacher.msgbuf.data.AbstractDataObject {

	/**
	 * Creates a {@link de.haumacher.mailcheck.cli.model.HarvestedEmail} instance.
	 */
	public static de.haumacher.mailcheck.cli.model.HarvestedEmail create() {
		return new de.haumacher.mailcheck.cli.model.HarvestedEmail();
	}

	/** Identifier for the {@link de.haumacher.mailcheck.cli.model.HarvestedEmail} type in JSON format. */
	public static final String HARVESTED_EMAIL__TYPE = "HarvestedEmail";

	/** @see #getEmail() */
	private static final String EMAIL__PROP = "email";

	/** @see #getType() */
	private static final String TYPE__PROP = "type";

	/** @see #getDomain() */
	private static final String DOMAIN__PROP = "domain";

	/** @see #getSource() */
	private static final String SOURCE__PROP = "source";

	/** @see #getOriginalEmail() */
	private static final String ORIGINAL_EMAIL__PROP = "originalEmail";

	private String _email = "";

	private String _type = "";

	private String _domain = "";

	private String _source = "";

	private String _originalEmail = "";

	/**
	 * Creates a {@link HarvestedEmail} instance.
	 *
	 * @see de.haumacher.mailcheck.cli.model.HarvestedEmail#create()
	 */
	protected HarvestedEmail() {
		super();
	}

	/**
	 * The normalized e-mail address.
	 */
	public final String getEmail() {
		return _email;
	}

	/**
	 * @see #getEmail()
	 */
	public de.haumacher.mailcheck.cli.model.HarvestedEmail setEmail(String value) {
		internalSetEmail(value);
		return this;
	}

	/** Internal setter for {@link #getEmail()} without chain call utility. */
	protected final void internalSetEmail(String value) {
		_email = value;
	}

	/**
	 * The type reported by the provider ({@code "gmail"}, {@code "microsoft"}, {@code "domain"}).
	 */
	public final String getType() {
		return _type;
	}

	/**
	 * @see #getType()
	 */
	public de.haumacher.mailcheck.cli.model.HarvestedEmail setType(String value) {
		internalSetType(value);
		return this;
	}

	/** Internal setter for {@link #getType()} without chain call utility. */
	protected final void internalSetType(String value) {
		_type = value;
	}

	/**
	 * The domain as reported by the provider (before normalization).
	 */
	public final String getDomain() {
		return _domain;
	}

	/**
	 * @see #getDomain()
	 */
	public de.haumacher.mailcheck.cli.model.HarvestedEmail setDomain(String value) {
		internalSetDomain(value);
		return this;
	}

	/** Internal setter for {@link #getDomain()} without chain call utility. */
	protected final void internalSetDomain(String value) {
		_domain = value;
	}

	/**
	 * Identifier of the provider that produced this entry.
	 */
	public final String getSource() {
		return _source;
	}

	/**
	 * @see #getSource()
	 */
	public de.haumacher.mailcheck.cli.model.HarvestedEmail setSource(String value) {
		internalSetSource(value);
		return this;
	}

	/** Internal setter for {@link #getSource()} without chain call utility. */
	protected final void internalSetSource(String value) {
		_source = value;
	}

	/**
	 * The original (non-normalized) e-mail address, if different from {@link #getEmail()}.
	 */
	public final String getOriginalEmail() {
		return _originalEmail;
	}

	/**
	 * @see #getOriginalEmail()
	 */
	public de.haumacher.mailcheck.cli.model.HarvestedEmail setOriginalEmail(String value) {
		internalSetOriginalEmail(value);
		return this;
	}

	/** Internal setter for {@link #getOriginalEmail()} without chain call utility. */
	protected final void internalSetOriginalEmail(String value) {
		_originalEmail = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.mailcheck.cli.model.HarvestedEmail readHarvestedEmail(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.mailcheck.cli.model.HarvestedEmail result = new de.haumacher.mailcheck.cli.model.HarvestedEmail();
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
		out.name(EMAIL__PROP);
		out.value(getEmail());
		out.name(TYPE__PROP);
		out.value(getType());
		out.name(DOMAIN__PROP);
		out.value(getDomain());
		out.name(SOURCE__PROP);
		out.value(getSource());
		out.name(ORIGINAL_EMAIL__PROP);
		out.value(getOriginalEmail());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case EMAIL__PROP: setEmail(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TYPE__PROP: setType(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DOMAIN__PROP: setDomain(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case SOURCE__PROP: setSource(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ORIGINAL_EMAIL__PROP: setOriginalEmail(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

}
