package de.haumacher.phoneblock.app.api.model;

/**
 * Response from account settings operations.
 */
public class AccountSettings extends AccountData {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.AccountSettings} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.AccountSettings create() {
		return new de.haumacher.phoneblock.app.api.model.AccountSettings();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.AccountSettings} type in JSON format. */
	public static final String ACCOUNT_SETTINGS__TYPE = "AccountSettings";

	/** @see #getLogin() */
	private static final String LOGIN__PROP = "login";

	/** @see #getEmail() */
	private static final String EMAIL__PROP = "email";

	/** @see #getMinVotes() */
	private static final String MIN_VOTES__PROP = "minVotes";

	/** @see #isWildcards() */
	private static final String WILDCARDS__PROP = "wildcards";

	private String _login = null;

	private String _email = null;

	private int _minVotes = 0;

	private boolean _wildcards = false;

	/**
	 * Creates a {@link AccountSettings} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.AccountSettings#create()
	 */
	protected AccountSettings() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.ACCOUNT_SETTINGS;
	}

	/**
	 * The user's login name (used for CardDAV URL construction).
	 */
	public final String getLogin() {
		return _login;
	}

	/**
	 * @see #getLogin()
	 */
	public de.haumacher.phoneblock.app.api.model.AccountSettings setLogin(String value) {
		internalSetLogin(value);
		return this;
	}

	/** Internal setter for {@link #getLogin()} without chain call utility. */
	protected final void internalSetLogin(String value) {
		_login = value;
	}

	/**
	 * Checks, whether {@link #getLogin()} has a value.
	 */
	public final boolean hasLogin() {
		return _login != null;
	}

	/**
	 * The user's email address.
	 */
	public final String getEmail() {
		return _email;
	}

	/**
	 * @see #getEmail()
	 */
	public de.haumacher.phoneblock.app.api.model.AccountSettings setEmail(String value) {
		internalSetEmail(value);
		return this;
	}

	/** Internal setter for {@link #getEmail()} without chain call utility. */
	protected final void internalSetEmail(String value) {
		_email = value;
	}

	/**
	 * Checks, whether {@link #getEmail()} has a value.
	 */
	public final boolean hasEmail() {
		return _email != null;
	}

	/**
	 * The minimum confidence ("Mindest-Konfidenz") configured in the account settings: the
	 * number of votes a number needs before the user's devices treat it as SPAM. Clients
	 * without a setting of their own (e.g. a self-hosted answerbot) use this value.
	 */
	public final int getMinVotes() {
		return _minVotes;
	}

	/**
	 * @see #getMinVotes()
	 */
	public de.haumacher.phoneblock.app.api.model.AccountSettings setMinVotes(int value) {
		internalSetMinVotes(value);
		return this;
	}

	/** Internal setter for {@link #getMinVotes()} without chain call utility. */
	protected final void internalSetMinVotes(int value) {
		_minVotes = value;
	}

	/**
	 * Whether the account settings enable wildcard (number range) blocking.
	 */
	public final boolean isWildcards() {
		return _wildcards;
	}

	/**
	 * @see #isWildcards()
	 */
	public de.haumacher.phoneblock.app.api.model.AccountSettings setWildcards(boolean value) {
		internalSetWildcards(value);
		return this;
	}

	/** Internal setter for {@link #isWildcards()} without chain call utility. */
	protected final void internalSetWildcards(boolean value) {
		_wildcards = value;
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.AccountSettings setLang(String value) {
		internalSetLang(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.AccountSettings setDialPrefix(String value) {
		internalSetDialPrefix(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.AccountSettings setDisplayName(String value) {
		internalSetDisplayName(value);
		return this;
	}

	@Override
	public String jsonType() {
		return ACCOUNT_SETTINGS__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.AccountSettings readAccountSettings(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.AccountSettings result = new de.haumacher.phoneblock.app.api.model.AccountSettings();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		if (hasLogin()) {
			out.name(LOGIN__PROP);
			out.value(getLogin());
		}
		if (hasEmail()) {
			out.name(EMAIL__PROP);
			out.value(getEmail());
		}
		out.name(MIN_VOTES__PROP);
		out.value(getMinVotes());
		out.name(WILDCARDS__PROP);
		out.value(isWildcards());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case LOGIN__PROP: setLogin(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case EMAIL__PROP: setEmail(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MIN_VOTES__PROP: setMinVotes(in.nextInt()); break;
			case WILDCARDS__PROP: setWildcards(in.nextBoolean()); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.AccountSettings} type. */
	public static final String ACCOUNT_SETTINGS__XML_ELEMENT = "account-settings";

	/** XML attribute or element name of a {@link #getLogin} property. */
	private static final String LOGIN__XML_ATTR = "login";

	/** XML attribute or element name of a {@link #getEmail} property. */
	private static final String EMAIL__XML_ATTR = "email";

	/** XML attribute or element name of a {@link #getMinVotes} property. */
	private static final String MIN_VOTES__XML_ATTR = "min-votes";

	/** XML attribute or element name of a {@link #isWildcards} property. */
	private static final String WILDCARDS__XML_ATTR = "wildcards";

	@Override
	public String getXmlTagName() {
		return ACCOUNT_SETTINGS__XML_ELEMENT;
	}

	/** Serializes all fields that are written as XML attributes. */
	@Override
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeAttributes(out);
		out.writeAttribute(LOGIN__XML_ATTR, getLogin());
		out.writeAttribute(EMAIL__XML_ATTR, getEmail());
		out.writeAttribute(MIN_VOTES__XML_ATTR, Integer.toString(getMinVotes()));
		out.writeAttribute(WILDCARDS__XML_ATTR, Boolean.toString(isWildcards()));
	}

	/** Serializes all fields that are written as XML elements. */
	@Override
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeElements(out);
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.AccountSettings} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AccountSettings readAccountSettings_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		AccountSettings result = new AccountSettings();
		result.readContentXml(in);
		return result;
	}

	@Override
	protected void readFieldXmlAttribute(String name, String value) {
		switch (name) {
			case LOGIN__XML_ATTR: {
				setLogin(value);
				break;
			}
			case EMAIL__XML_ATTR: {
				setEmail(value);
				break;
			}
			case MIN_VOTES__XML_ATTR: {
				setMinVotes(Integer.parseInt(value));
				break;
			}
			case WILDCARDS__XML_ATTR: {
				setWildcards(Boolean.parseBoolean(value));
				break;
			}
			default: {
				super.readFieldXmlAttribute(name, value);
			}
		}
	}

	@Override
	protected void readFieldXmlElement(javax.xml.stream.XMLStreamReader in, String localName) throws javax.xml.stream.XMLStreamException {
		switch (localName) {
			case LOGIN__XML_ATTR: {
				setLogin(in.getElementText());
				break;
			}
			case EMAIL__XML_ATTR: {
				setEmail(in.getElementText());
				break;
			}
			case MIN_VOTES__XML_ATTR: {
				setMinVotes(Integer.parseInt(in.getElementText()));
				break;
			}
			case WILDCARDS__XML_ATTR: {
				setWildcards(Boolean.parseBoolean(in.getElementText()));
				break;
			}
			default: {
				super.readFieldXmlElement(in, localName);
			}
		}
	}

	/** Creates a new {@link AccountSettings} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AccountSettings readAccountSettings(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.AccountSettings.readAccountSettings_XmlContent(in);
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.app.api.model.AccountData.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
