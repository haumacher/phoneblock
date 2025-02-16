package de.haumacher.phoneblock.db.settings;

/**
 * Account settings.
 */
public class UserSettings extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.settings.UserSettings} instance.
	 */
	public static de.haumacher.phoneblock.db.settings.UserSettings create() {
		return new de.haumacher.phoneblock.db.settings.UserSettings();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.UserSettings} type in JSON format. */
	public static final String USER_SETTINGS__TYPE = "UserSettings";

	/** @see #getId() */
	public static final String ID__PROP = "id";

	/** @see #getLogin() */
	public static final String LOGIN__PROP = "login";

	/** @see #getDisplayName() */
	public static final String DISPLAY_NAME__PROP = "displayName";

	/** @see #getEmail() */
	public static final String EMAIL__PROP = "email";

	/** @see #getMinVotes() */
	public static final String MIN_VOTES__PROP = "minVotes";

	/** @see #getMaxLength() */
	public static final String MAX_LENGTH__PROP = "maxLength";

	/** @see #isWildcards() */
	public static final String WILDCARDS__PROP = "wildcards";

	/** @see #getLastAccess() */
	public static final String LAST_ACCESS__PROP = "lastAccess";

	/** @see #getCredit() */
	public static final String CREDIT__PROP = "credit";

	/** Identifier for the property {@link #getId()} in binary format. */
	static final int ID__ID = 1;

	/** Identifier for the property {@link #getLogin()} in binary format. */
	static final int LOGIN__ID = 2;

	/** Identifier for the property {@link #getDisplayName()} in binary format. */
	static final int DISPLAY_NAME__ID = 3;

	/** Identifier for the property {@link #getEmail()} in binary format. */
	static final int EMAIL__ID = 4;

	/** Identifier for the property {@link #getMinVotes()} in binary format. */
	static final int MIN_VOTES__ID = 5;

	/** Identifier for the property {@link #getMaxLength()} in binary format. */
	static final int MAX_LENGTH__ID = 6;

	/** Identifier for the property {@link #isWildcards()} in binary format. */
	static final int WILDCARDS__ID = 7;

	/** Identifier for the property {@link #getLastAccess()} in binary format. */
	static final int LAST_ACCESS__ID = 8;

	/** Identifier for the property {@link #getCredit()} in binary format. */
	static final int CREDIT__ID = 9;

	private long _id = 0L;

	private String _login = "";

	private String _displayName = "";

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
		_listener.beforeSet(this, ID__PROP, value);
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
		_listener.beforeSet(this, LOGIN__PROP, value);
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
		_listener.beforeSet(this, DISPLAY_NAME__PROP, value);
		_displayName = value;
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
		_listener.beforeSet(this, EMAIL__PROP, value);
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
		_listener.beforeSet(this, MIN_VOTES__PROP, value);
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
		_listener.beforeSet(this, MAX_LENGTH__PROP, value);
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
		_listener.beforeSet(this, WILDCARDS__PROP, value);
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
		_listener.beforeSet(this, LAST_ACCESS__PROP, value);
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
		_listener.beforeSet(this, CREDIT__PROP, value);
		_credit = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.settings.UserSettings registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.settings.UserSettings unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return USER_SETTINGS__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ID__PROP, 
			LOGIN__PROP, 
			DISPLAY_NAME__PROP, 
			EMAIL__PROP, 
			MIN_VOTES__PROP, 
			MAX_LENGTH__PROP, 
			WILDCARDS__PROP, 
			LAST_ACCESS__PROP, 
			CREDIT__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ID__PROP: return getId();
			case LOGIN__PROP: return getLogin();
			case DISPLAY_NAME__PROP: return getDisplayName();
			case EMAIL__PROP: return getEmail();
			case MIN_VOTES__PROP: return getMinVotes();
			case MAX_LENGTH__PROP: return getMaxLength();
			case WILDCARDS__PROP: return isWildcards();
			case LAST_ACCESS__PROP: return getLastAccess();
			case CREDIT__PROP: return getCredit();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ID__PROP: internalSetId((long) value); break;
			case LOGIN__PROP: internalSetLogin((String) value); break;
			case DISPLAY_NAME__PROP: internalSetDisplayName((String) value); break;
			case EMAIL__PROP: internalSetEmail((String) value); break;
			case MIN_VOTES__PROP: internalSetMinVotes((int) value); break;
			case MAX_LENGTH__PROP: internalSetMaxLength((int) value); break;
			case WILDCARDS__PROP: internalSetWildcards((boolean) value); break;
			case LAST_ACCESS__PROP: internalSetLastAccess((long) value); break;
			case CREDIT__PROP: internalSetCredit((int) value); break;
		}
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
			case EMAIL__PROP: setEmail(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MIN_VOTES__PROP: setMinVotes(in.nextInt()); break;
			case MAX_LENGTH__PROP: setMaxLength(in.nextInt()); break;
			case WILDCARDS__PROP: setWildcards(in.nextBoolean()); break;
			case LAST_ACCESS__PROP: setLastAccess(in.nextLong()); break;
			case CREDIT__PROP: setCredit(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.beginObject();
		writeFields(out);
		out.endObject();
	}

	/**
	 * Serializes all fields of this instance to the given binary output.
	 *
	 * @param out
	 *        The binary output to write to.
	 * @throws java.io.IOException If writing fails.
	 */
	protected void writeFields(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.name(ID__ID);
		out.value(getId());
		out.name(LOGIN__ID);
		out.value(getLogin());
		out.name(DISPLAY_NAME__ID);
		out.value(getDisplayName());
		out.name(EMAIL__ID);
		out.value(getEmail());
		out.name(MIN_VOTES__ID);
		out.value(getMinVotes());
		out.name(MAX_LENGTH__ID);
		out.value(getMaxLength());
		out.name(WILDCARDS__ID);
		out.value(isWildcards());
		out.name(LAST_ACCESS__ID);
		out.value(getLastAccess());
		out.name(CREDIT__ID);
		out.value(getCredit());
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.UserSettings readUserSettings(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.db.settings.UserSettings result = de.haumacher.phoneblock.db.settings.UserSettings.readUserSettings_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.db.settings.UserSettings} from a polymorphic composition. */
	public static de.haumacher.phoneblock.db.settings.UserSettings readUserSettings_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.UserSettings result = new UserSettings();
		result.readContent(in);
		return result;
	}

	/** Helper for reading all fields of this instance. */
	protected final void readContent(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		while (in.hasNext()) {
			int field = in.nextName();
			readField(in, field);
		}
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case ID__ID: setId(in.nextLong()); break;
			case LOGIN__ID: setLogin(in.nextString()); break;
			case DISPLAY_NAME__ID: setDisplayName(in.nextString()); break;
			case EMAIL__ID: setEmail(in.nextString()); break;
			case MIN_VOTES__ID: setMinVotes(in.nextInt()); break;
			case MAX_LENGTH__ID: setMaxLength(in.nextInt()); break;
			case WILDCARDS__ID: setWildcards(in.nextBoolean()); break;
			case LAST_ACCESS__ID: setLastAccess(in.nextLong()); break;
			case CREDIT__ID: setCredit(in.nextInt()); break;
			default: in.skipValue(); 
		}
	}

}
