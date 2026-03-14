package de.haumacher.phoneblock.db.settings;

/**
 * A user's authorization token.
 */
public class AuthToken extends de.haumacher.msgbuf.data.AbstractDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.settings.AuthToken} instance.
	 */
	public static de.haumacher.phoneblock.db.settings.AuthToken create() {
		return new de.haumacher.phoneblock.db.settings.AuthToken();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.AuthToken} type in JSON format. */
	public static final String AUTH_TOKEN__TYPE = "AuthToken";

	/** @see #getId() */
	private static final String ID__PROP = "id";

	/** @see #getUserId() */
	private static final String USER_ID__PROP = "userId";

	/** @see #getUserName() */
	private static final String USER_NAME__PROP = "userName";

	/** @see #getLabel() */
	private static final String LABEL__PROP = "label";

	/** @see #getCreated() */
	private static final String CREATED__PROP = "created";

	/** @see #getPwHash() */
	private static final String PW_HASH__PROP = "pwHash";

	/** @see #isImplicit() */
	private static final String IMPLICIT__PROP = "implicit";

	/** @see #isAccessQuery() */
	private static final String ACCESS_QUERY__PROP = "accessQuery";

	/** @see #isAccessDownload() */
	private static final String ACCESS_DOWNLOAD__PROP = "accessDownload";

	/** @see #isAccessCarddav() */
	private static final String ACCESS_CARDDAV__PROP = "accessCarddav";

	/** @see #isAccessRate() */
	private static final String ACCESS_RATE__PROP = "accessRate";

	/** @see #isAccessLogin() */
	private static final String ACCESS_LOGIN__PROP = "accessLogin";

	/** @see #getLastAccess() */
	private static final String LAST_ACCESS__PROP = "lastAccess";

	/** @see #getUserAgent() */
	private static final String USER_AGENT__PROP = "userAgent";

	/** @see #getToken() */
	private static final String TOKEN__PROP = "token";

	private long _id = 0L;

	private long _userId = 0L;

	private String _userName = "";

	private String _label = "";

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
		_userName = value;
	}

	/**
	 * A user-defined label for this token (for explicitly created tokens only).
	 */
	public final String getLabel() {
		return _label;
	}

	/**
	 * @see #getLabel()
	 */
	public de.haumacher.phoneblock.db.settings.AuthToken setLabel(String value) {
		internalSetLabel(value);
		return this;
	}

	/** Internal setter for {@link #getLabel()} without chain call utility. */
	protected final void internalSetLabel(String value) {
		_label = value;
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
		_token = value;
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
		out.name(LABEL__PROP);
		out.value(getLabel());
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
			case LABEL__PROP: setLabel(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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

}
