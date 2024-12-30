package de.haumacher.phoneblock.db.settings;

/**
 * A user's authorization token.
 */
public class AuthToken extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.settings.AuthToken} instance.
	 */
	public static de.haumacher.phoneblock.db.settings.AuthToken create() {
		return new de.haumacher.phoneblock.db.settings.AuthToken();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.AuthToken} type in JSON format. */
	public static final String AUTH_TOKEN__TYPE = "AuthToken";

	/** @see #getId() */
	public static final String ID__PROP = "id";

	/** @see #getUserId() */
	public static final String USER_ID__PROP = "userId";

	/** @see #getUserName() */
	public static final String USER_NAME__PROP = "userName";

	/** @see #getCreated() */
	public static final String CREATED__PROP = "created";

	/** @see #getPwHash() */
	public static final String PW_HASH__PROP = "pwHash";

	/** @see #isImplicit() */
	public static final String IMPLICIT__PROP = "implicit";

	/** @see #isAccessQuery() */
	public static final String ACCESS_QUERY__PROP = "accessQuery";

	/** @see #isAccessDownload() */
	public static final String ACCESS_DOWNLOAD__PROP = "accessDownload";

	/** @see #isAccessCarddav() */
	public static final String ACCESS_CARDDAV__PROP = "accessCarddav";

	/** @see #isAccessRate() */
	public static final String ACCESS_RATE__PROP = "accessRate";

	/** @see #isAccessLogin() */
	public static final String ACCESS_LOGIN__PROP = "accessLogin";

	/** @see #getLastAccess() */
	public static final String LAST_ACCESS__PROP = "lastAccess";

	/** @see #getUserAgent() */
	public static final String USER_AGENT__PROP = "userAgent";

	/** @see #getToken() */
	public static final String TOKEN__PROP = "token";

	/** Identifier for the property {@link #getId()} in binary format. */
	static final int ID__ID = 1;

	/** Identifier for the property {@link #getUserId()} in binary format. */
	static final int USER_ID__ID = 2;

	/** Identifier for the property {@link #getUserName()} in binary format. */
	static final int USER_NAME__ID = 3;

	/** Identifier for the property {@link #getCreated()} in binary format. */
	static final int CREATED__ID = 4;

	/** Identifier for the property {@link #getPwHash()} in binary format. */
	static final int PW_HASH__ID = 5;

	/** Identifier for the property {@link #isImplicit()} in binary format. */
	static final int IMPLICIT__ID = 6;

	/** Identifier for the property {@link #isAccessQuery()} in binary format. */
	static final int ACCESS_QUERY__ID = 7;

	/** Identifier for the property {@link #isAccessDownload()} in binary format. */
	static final int ACCESS_DOWNLOAD__ID = 8;

	/** Identifier for the property {@link #isAccessCarddav()} in binary format. */
	static final int ACCESS_CARDDAV__ID = 9;

	/** Identifier for the property {@link #isAccessRate()} in binary format. */
	static final int ACCESS_RATE__ID = 10;

	/** Identifier for the property {@link #isAccessLogin()} in binary format. */
	static final int ACCESS_LOGIN__ID = 11;

	/** Identifier for the property {@link #getLastAccess()} in binary format. */
	static final int LAST_ACCESS__ID = 12;

	/** Identifier for the property {@link #getUserAgent()} in binary format. */
	static final int USER_AGENT__ID = 13;

	/** Identifier for the property {@link #getToken()} in binary format. */
	static final int TOKEN__ID = 14;

	private long _id = 0L;

	private long _userId = 0L;

	private String _userName = "";

	private long _created = 0L;

	private byte[] _pwHash = null;

	private boolean _implicit = false;

	private boolean _accessQuery = false;

	private boolean _accessDownload = false;

	private boolean _accessCarddav = false;

	private boolean _accessRate = false;

	private boolean _accessLogin = false;

	private long _lastAccess = 0L;

	private String _userAgent = "";

	private String _token = "";

	/**
	 * Creates a {@link AuthToken} instance.
	 *
	 * @see de.haumacher.phoneblock.db.settings.AuthToken#create()
	 */
	protected AuthToken() {
		super();
	}

	/**
	 * The token ID
	 */
	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_listener.beforeSet(this, ID__PROP, value);
		_id = value;
	}

	/**
	 * The ID of the user that owns this token.
	 */
	public final long getUserId() {
		return _userId;
	}

	/**
	 * @see #getUserId()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setUserId(long value) {
		internalSetUserId(value);
		return this;
	}

	/** Internal setter for {@link #getUserId()} without chain call utility. */
	protected final void internalSetUserId(long value) {
		_listener.beforeSet(this, USER_ID__PROP, value);
		_userId = value;
	}

	/**
	 * The login name of the user that owns this token.
	 */
	public final String getUserName() {
		return _userName;
	}

	/**
	 * @see #getUserName()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setUserName(String value) {
		internalSetUserName(value);
		return this;
	}

	/** Internal setter for {@link #getUserName()} without chain call utility. */
	protected final void internalSetUserName(String value) {
		_listener.beforeSet(this, USER_NAME__PROP, value);
		_userName = value;
	}

	/**
	 * Time when this token was created
	 */
	public final long getCreated() {
		return _created;
	}

	/**
	 * @see #getCreated()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setCreated(long value) {
		internalSetCreated(value);
		return this;
	}

	/** Internal setter for {@link #getCreated()} without chain call utility. */
	protected final void internalSetCreated(long value) {
		_listener.beforeSet(this, CREATED__PROP, value);
		_created = value;
	}

	/**
	 * The hash of this token's secret.
	 */
	public final byte[] getPwHash() {
		return _pwHash;
	}

	/**
	 * @see #getPwHash()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setPwHash(byte[] value) {
		internalSetPwHash(value);
		return this;
	}

	/** Internal setter for {@link #getPwHash()} without chain call utility. */
	protected final void internalSetPwHash(byte[] value) {
		_listener.beforeSet(this, PW_HASH__PROP, value);
		_pwHash = value;
	}

	/**
	 * Whether this is an implicitly created token for the "stay logged-in" functionality.
	 */
	public final boolean isImplicit() {
		return _implicit;
	}

	/**
	 * @see #isImplicit()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setImplicit(boolean value) {
		internalSetImplicit(value);
		return this;
	}

	/** Internal setter for {@link #isImplicit()} without chain call utility. */
	protected final void internalSetImplicit(boolean value) {
		_listener.beforeSet(this, IMPLICIT__PROP, value);
		_implicit = value;
	}

	/**
	 * Whether this token has access to the query API.
	 */
	public final boolean isAccessQuery() {
		return _accessQuery;
	}

	/**
	 * @see #isAccessQuery()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setAccessQuery(boolean value) {
		internalSetAccessQuery(value);
		return this;
	}

	/** Internal setter for {@link #isAccessQuery()} without chain call utility. */
	protected final void internalSetAccessQuery(boolean value) {
		_listener.beforeSet(this, ACCESS_QUERY__PROP, value);
		_accessQuery = value;
	}

	/**
	 * Whether this token has access to the blocklist download API.
	 */
	public final boolean isAccessDownload() {
		return _accessDownload;
	}

	/**
	 * @see #isAccessDownload()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setAccessDownload(boolean value) {
		internalSetAccessDownload(value);
		return this;
	}

	/** Internal setter for {@link #isAccessDownload()} without chain call utility. */
	protected final void internalSetAccessDownload(boolean value) {
		_listener.beforeSet(this, ACCESS_DOWNLOAD__PROP, value);
		_accessDownload = value;
	}

	/**
	 * Whether this token has access to the CARDDAV synchronization API.
	 */
	public final boolean isAccessCarddav() {
		return _accessCarddav;
	}

	/**
	 * @see #isAccessCarddav()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setAccessCarddav(boolean value) {
		internalSetAccessCarddav(value);
		return this;
	}

	/** Internal setter for {@link #isAccessCarddav()} without chain call utility. */
	protected final void internalSetAccessCarddav(boolean value) {
		_listener.beforeSet(this, ACCESS_CARDDAV__PROP, value);
		_accessCarddav = value;
	}

	/**
	 * Whether this token has access to the rate API.
	 */
	public final boolean isAccessRate() {
		return _accessRate;
	}

	/**
	 * @see #isAccessRate()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setAccessRate(boolean value) {
		internalSetAccessRate(value);
		return this;
	}

	/** Internal setter for {@link #isAccessRate()} without chain call utility. */
	protected final void internalSetAccessRate(boolean value) {
		_listener.beforeSet(this, ACCESS_RATE__PROP, value);
		_accessRate = value;
	}

	/**
	 * Whether this token is allowed to log-in to the website.
	 */
	public final boolean isAccessLogin() {
		return _accessLogin;
	}

	/**
	 * @see #isAccessLogin()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setAccessLogin(boolean value) {
		internalSetAccessLogin(value);
		return this;
	}

	/** Internal setter for {@link #isAccessLogin()} without chain call utility. */
	protected final void internalSetAccessLogin(boolean value) {
		_listener.beforeSet(this, ACCESS_LOGIN__PROP, value);
		_accessLogin = value;
	}

	/**
	 * When this token was used or updated the last time.
	 */
	public final long getLastAccess() {
		return _lastAccess;
	}

	/**
	 * @see #getLastAccess()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setLastAccess(long value) {
		internalSetLastAccess(value);
		return this;
	}

	/** Internal setter for {@link #getLastAccess()} without chain call utility. */
	protected final void internalSetLastAccess(long value) {
		_listener.beforeSet(this, LAST_ACCESS__PROP, value);
		_lastAccess = value;
	}

	/**
	 * The user agent that used this token the last time.
	 */
	public final String getUserAgent() {
		return _userAgent;
	}

	/**
	 * @see #getUserAgent()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setUserAgent(String value) {
		internalSetUserAgent(value);
		return this;
	}

	/** Internal setter for {@link #getUserAgent()} without chain call utility. */
	protected final void internalSetUserAgent(String value) {
		_listener.beforeSet(this, USER_AGENT__PROP, value);
		_userAgent = value;
	}

	/**
	 * The encoded token that must be used by the client for authorization.
	 */
	public final String getToken() {
		return _token;
	}

	/**
	 * @see #getToken()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setToken(String value) {
		internalSetToken(value);
		return this;
	}

	/** Internal setter for {@link #getToken()} without chain call utility. */
	protected final void internalSetToken(String value) {
		_listener.beforeSet(this, TOKEN__PROP, value);
		_token = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.settings.AuthToken registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.settings.AuthToken unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return AUTH_TOKEN__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ID__PROP, 
			USER_ID__PROP, 
			USER_NAME__PROP, 
			CREATED__PROP, 
			PW_HASH__PROP, 
			IMPLICIT__PROP, 
			ACCESS_QUERY__PROP, 
			ACCESS_DOWNLOAD__PROP, 
			ACCESS_CARDDAV__PROP, 
			ACCESS_RATE__PROP, 
			ACCESS_LOGIN__PROP, 
			LAST_ACCESS__PROP, 
			USER_AGENT__PROP, 
			TOKEN__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ID__PROP: return getId();
			case USER_ID__PROP: return getUserId();
			case USER_NAME__PROP: return getUserName();
			case CREATED__PROP: return getCreated();
			case PW_HASH__PROP: return getPwHash();
			case IMPLICIT__PROP: return isImplicit();
			case ACCESS_QUERY__PROP: return isAccessQuery();
			case ACCESS_DOWNLOAD__PROP: return isAccessDownload();
			case ACCESS_CARDDAV__PROP: return isAccessCarddav();
			case ACCESS_RATE__PROP: return isAccessRate();
			case ACCESS_LOGIN__PROP: return isAccessLogin();
			case LAST_ACCESS__PROP: return getLastAccess();
			case USER_AGENT__PROP: return getUserAgent();
			case TOKEN__PROP: return getToken();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ID__PROP: internalSetId((long) value); break;
			case USER_ID__PROP: internalSetUserId((long) value); break;
			case USER_NAME__PROP: internalSetUserName((String) value); break;
			case CREATED__PROP: internalSetCreated((long) value); break;
			case PW_HASH__PROP: internalSetPwHash((byte[]) value); break;
			case IMPLICIT__PROP: internalSetImplicit((boolean) value); break;
			case ACCESS_QUERY__PROP: internalSetAccessQuery((boolean) value); break;
			case ACCESS_DOWNLOAD__PROP: internalSetAccessDownload((boolean) value); break;
			case ACCESS_CARDDAV__PROP: internalSetAccessCarddav((boolean) value); break;
			case ACCESS_RATE__PROP: internalSetAccessRate((boolean) value); break;
			case ACCESS_LOGIN__PROP: internalSetAccessLogin((boolean) value); break;
			case LAST_ACCESS__PROP: internalSetLastAccess((long) value); break;
			case USER_AGENT__PROP: internalSetUserAgent((String) value); break;
			case TOKEN__PROP: internalSetToken((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.AuthToken readAuthToken(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.AuthToken result = new de.haumacher.phoneblock.db.settings.AuthToken();
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
		out.name(USER_ID__PROP);
		out.value(getUserId());
		out.name(USER_NAME__PROP);
		out.value(getUserName());
		out.name(CREATED__PROP);
		out.value(getCreated());
		out.name(PW_HASH__PROP);
		de.haumacher.msgbuf.json.JsonUtil.writeBinaryOptional(out, getPwHash());
		out.name(IMPLICIT__PROP);
		out.value(isImplicit());
		out.name(ACCESS_QUERY__PROP);
		out.value(isAccessQuery());
		out.name(ACCESS_DOWNLOAD__PROP);
		out.value(isAccessDownload());
		out.name(ACCESS_CARDDAV__PROP);
		out.value(isAccessCarddav());
		out.name(ACCESS_RATE__PROP);
		out.value(isAccessRate());
		out.name(ACCESS_LOGIN__PROP);
		out.value(isAccessLogin());
		out.name(LAST_ACCESS__PROP);
		out.value(getLastAccess());
		out.name(USER_AGENT__PROP);
		out.value(getUserAgent());
		out.name(TOKEN__PROP);
		out.value(getToken());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(in.nextLong()); break;
			case USER_ID__PROP: setUserId(in.nextLong()); break;
			case USER_NAME__PROP: setUserName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CREATED__PROP: setCreated(in.nextLong()); break;
			case PW_HASH__PROP: setPwHash(de.haumacher.msgbuf.json.JsonUtil.nextBinaryOptional(in)); break;
			case IMPLICIT__PROP: setImplicit(in.nextBoolean()); break;
			case ACCESS_QUERY__PROP: setAccessQuery(in.nextBoolean()); break;
			case ACCESS_DOWNLOAD__PROP: setAccessDownload(in.nextBoolean()); break;
			case ACCESS_CARDDAV__PROP: setAccessCarddav(in.nextBoolean()); break;
			case ACCESS_RATE__PROP: setAccessRate(in.nextBoolean()); break;
			case ACCESS_LOGIN__PROP: setAccessLogin(in.nextBoolean()); break;
			case LAST_ACCESS__PROP: setLastAccess(in.nextLong()); break;
			case USER_AGENT__PROP: setUserAgent(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TOKEN__PROP: setToken(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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
		out.name(USER_ID__ID);
		out.value(getUserId());
		out.name(USER_NAME__ID);
		out.value(getUserName());
		out.name(CREATED__ID);
		out.value(getCreated());
		out.name(PW_HASH__ID);
		out.value(getPwHash());
		out.name(IMPLICIT__ID);
		out.value(isImplicit());
		out.name(ACCESS_QUERY__ID);
		out.value(isAccessQuery());
		out.name(ACCESS_DOWNLOAD__ID);
		out.value(isAccessDownload());
		out.name(ACCESS_CARDDAV__ID);
		out.value(isAccessCarddav());
		out.name(ACCESS_RATE__ID);
		out.value(isAccessRate());
		out.name(ACCESS_LOGIN__ID);
		out.value(isAccessLogin());
		out.name(LAST_ACCESS__ID);
		out.value(getLastAccess());
		out.name(USER_AGENT__ID);
		out.value(getUserAgent());
		out.name(TOKEN__ID);
		out.value(getToken());
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.AuthToken readAuthToken(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.db.settings.AuthToken result = de.haumacher.phoneblock.db.settings.AuthToken.readAuthToken_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.db.settings.AuthToken} from a polymorphic composition. */
	public static de.haumacher.phoneblock.db.settings.AuthToken readAuthToken_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.AuthToken result = new AuthToken();
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
			case USER_ID__ID: setUserId(in.nextLong()); break;
			case USER_NAME__ID: setUserName(in.nextString()); break;
			case CREATED__ID: setCreated(in.nextLong()); break;
			case PW_HASH__ID: setPwHash(in.nextBinary()); break;
			case IMPLICIT__ID: setImplicit(in.nextBoolean()); break;
			case ACCESS_QUERY__ID: setAccessQuery(in.nextBoolean()); break;
			case ACCESS_DOWNLOAD__ID: setAccessDownload(in.nextBoolean()); break;
			case ACCESS_CARDDAV__ID: setAccessCarddav(in.nextBoolean()); break;
			case ACCESS_RATE__ID: setAccessRate(in.nextBoolean()); break;
			case ACCESS_LOGIN__ID: setAccessLogin(in.nextBoolean()); break;
			case LAST_ACCESS__ID: setLastAccess(in.nextLong()); break;
			case USER_AGENT__ID: setUserAgent(in.nextString()); break;
			case TOKEN__ID: setToken(in.nextString()); break;
			default: in.skipValue(); 
		}
	}

}
