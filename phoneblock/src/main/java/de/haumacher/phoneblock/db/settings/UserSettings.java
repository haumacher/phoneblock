package de.haumacher.phoneblock.db.settings;

/**
 * Account settings.
 */
public class UserSettings extends de.haumacher.msgbuf.data.AbstractDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.settings.UserSettings} instance.
	 */
	public static de.haumacher.phoneblock.db.settings.UserSettings create() {
		return new de.haumacher.phoneblock.db.settings.UserSettings();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.UserSettings} type in JSON format. */
	public static final String USER_SETTINGS__TYPE = "UserSettings";

	/** @see #getId() */
	private static final String ID__PROP = "id";

	/** @see #getLogin() */
	private static final String LOGIN__PROP = "login";

	/** @see #getDisplayName() */
	private static final String DISPLAY_NAME__PROP = "displayName";

	/** @see #getLang() */
	private static final String LANG__PROP = "lang";

	/** @see #getDialPrefix() */
	private static final String DIAL_PREFIX__PROP = "dialPrefix";

	/** @see #isNationalOnly() */
	private static final String NATIONAL_ONLY__PROP = "nationalOnly";

	/** @see #getEmail() */
	private static final String EMAIL__PROP = "email";

	/** @see #getMinVotes() */
	private static final String MIN_VOTES__PROP = "minVotes";

	/** @see #getMaxLength() */
	private static final String MAX_LENGTH__PROP = "maxLength";

	/** @see #isWildcards() */
	private static final String WILDCARDS__PROP = "wildcards";

	/** @see #getLastAccess() */
	private static final String LAST_ACCESS__PROP = "lastAccess";

	/** @see #getCredit() */
	private static final String CREDIT__PROP = "credit";

	private long _id = 0L;

	private String _login = "";

	private String _displayName = "";

	private String _lang = "";

	private String _dialPrefix = "";

	private boolean _nationalOnly = false;

	private String _email = "";

	private int _minVotes = 0;

	private int _maxLength = 0;

	private boolean _wildcards = false;

	private long _lastAccess = 0L;

	private int _credit = 0;

	/**
	 * Creates a {@link UserSettings} instance.
	 *
	 * @see de.haumacher.phoneblock.db.settings.UserSettings#create()
	 */
	protected UserSettings() {
		super();
	}

	/**
	 * The internal user ID.
	 */
	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_id = value;
	}

	/**
	 * The user's login name
	 */
	public final String getLogin() {
		return _login;
	}

	/**
	 * @see #getLogin()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setLogin(String value) {
		internalSetLogin(value);
		return this;
	}

	/** Internal setter for {@link #getLogin()} without chain call utility. */
	protected final void internalSetLogin(String value) {
		_login = value;
	}

	/**
	 * The user's real name.
	 */
	public final String getDisplayName() {
		return _displayName;
	}

	/**
	 * @see #getDisplayName()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setDisplayName(String value) {
		internalSetDisplayName(value);
		return this;
	}

	/** Internal setter for {@link #getDisplayName()} without chain call utility. */
	protected final void internalSetDisplayName(String value) {
		_displayName = value;
	}

	/**
	 * The preferred language of the user.
	 */
	public final String getLang() {
		return _lang;
	}

	/**
	 * @see #getLang()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setLang(String value) {
		internalSetLang(value);
		return this;
	}

	/** Internal setter for {@link #getLang()} without chain call utility. */
	protected final void internalSetLang(String value) {
		_lang = value;
	}

	/**
	 * The user's country dial prefix ("+49" for Germany).
	 */
	public final String getDialPrefix() {
		return _dialPrefix;
	}

	/**
	 * @see #getDialPrefix()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setDialPrefix(String value) {
		internalSetDialPrefix(value);
		return this;
	}

	/** Internal setter for {@link #getDialPrefix()} without chain call utility. */
	protected final void internalSetDialPrefix(String value) {
		_dialPrefix = value;
	}

	/**
	 * Whether the user's blocklist should contain nationl numbers only.
	 */
	public final boolean isNationalOnly() {
		return _nationalOnly;
	}

	/**
	 * @see #isNationalOnly()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setNationalOnly(boolean value) {
		internalSetNationalOnly(value);
		return this;
	}

	/** Internal setter for {@link #isNationalOnly()} without chain call utility. */
	protected final void internalSetNationalOnly(boolean value) {
		_nationalOnly = value;
	}

	/**
	 * The user's e-mail address.
	 */
	public final String getEmail() {
		return _email;
	}

	/**
	 * @see #getEmail()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setEmail(String value) {
		internalSetEmail(value);
		return this;
	}

	/** Internal setter for {@link #getEmail()} without chain call utility. */
	protected final void internalSetEmail(String value) {
		_email = value;
	}

	/**
	 * Minumum number of votes to create a blocklist entry.
	 */
	public final int getMinVotes() {
		return _minVotes;
	}

	/**
	 * @see #getMinVotes()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setMinVotes(int value) {
		internalSetMinVotes(value);
		return this;
	}

	/** Internal setter for {@link #getMinVotes()} without chain call utility. */
	protected final void internalSetMinVotes(int value) {
		_minVotes = value;
	}

	/**
	 * Maximum number of blocklist entries.
	 */
	public final int getMaxLength() {
		return _maxLength;
	}

	/**
	 * @see #getMaxLength()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setMaxLength(int value) {
		internalSetMaxLength(value);
		return this;
	}

	/** Internal setter for {@link #getMaxLength()} without chain call utility. */
	protected final void internalSetMaxLength(int value) {
		_maxLength = value;
	}

	/**
	 * Whether multiple adjacent numbers should be joined to a wildcard number.
	 */
	public final boolean isWildcards() {
		return _wildcards;
	}

	/**
	 * @see #isWildcards()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setWildcards(boolean value) {
		internalSetWildcards(value);
		return this;
	}

	/** Internal setter for {@link #isWildcards()} without chain call utility. */
	protected final void internalSetWildcards(boolean value) {
		_wildcards = value;
	}

	/**
	 * Timestamp when the user requested the blocklist last time
	 */
	public final long getLastAccess() {
		return _lastAccess;
	}

	/**
	 * @see #getLastAccess()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setLastAccess(long value) {
		internalSetLastAccess(value);
		return this;
	}

	/** Internal setter for {@link #getLastAccess()} without chain call utility. */
	protected final void internalSetLastAccess(long value) {
		_lastAccess = value;
	}

	/**
	 * The sum of donations done by this user in cent.
	 */
	public final int getCredit() {
		return _credit;
	}

	/**
	 * @see #getCredit()
	 */
	public de.haumacher.phoneblock.db.settings.UserSettings setCredit(int value) {
		internalSetCredit(value);
		return this;
	}

	/** Internal setter for {@link #getCredit()} without chain call utility. */
	protected final void internalSetCredit(int value) {
		_credit = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.UserSettings readUserSettings(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.UserSettings result = new de.haumacher.phoneblock.db.settings.UserSettings();
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
		out.name(ID__PROP);
		out.value(getId());
		out.name(LOGIN__PROP);
		out.value(getLogin());
		out.name(DISPLAY_NAME__PROP);
		out.value(getDisplayName());
		out.name(LANG__PROP);
		out.value(getLang());
		out.name(DIAL_PREFIX__PROP);
		out.value(getDialPrefix());
		out.name(NATIONAL_ONLY__PROP);
		out.value(isNationalOnly());
		out.name(EMAIL__PROP);
		out.value(getEmail());
		out.name(MIN_VOTES__PROP);
		out.value(getMinVotes());
		out.name(MAX_LENGTH__PROP);
		out.value(getMaxLength());
		out.name(WILDCARDS__PROP);
		out.value(isWildcards());
		out.name(LAST_ACCESS__PROP);
		out.value(getLastAccess());
		out.name(CREDIT__PROP);
		out.value(getCredit());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(in.nextLong()); break;
			case LOGIN__PROP: setLogin(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DISPLAY_NAME__PROP: setDisplayName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LANG__PROP: setLang(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DIAL_PREFIX__PROP: setDialPrefix(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case NATIONAL_ONLY__PROP: setNationalOnly(in.nextBoolean()); break;
			case EMAIL__PROP: setEmail(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MIN_VOTES__PROP: setMinVotes(in.nextInt()); break;
			case MAX_LENGTH__PROP: setMaxLength(in.nextInt()); break;
			case WILDCARDS__PROP: setWildcards(in.nextBoolean()); break;
			case LAST_ACCESS__PROP: setLastAccess(in.nextLong()); break;
			case CREDIT__PROP: setCredit(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

}
