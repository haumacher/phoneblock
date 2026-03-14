package de.haumacher.phoneblock.db.settings;

public class AnswerBotSip extends AnswerBotSetting {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.settings.AnswerBotSip} instance.
	 */
	public static de.haumacher.phoneblock.db.settings.AnswerBotSip create() {
		return new de.haumacher.phoneblock.db.settings.AnswerBotSip();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.AnswerBotSip} type in JSON format. */
	public static final String ANSWER_BOT_SIP__TYPE = "AnswerBotSip";

	/** @see #getHost() */
	private static final String HOST__PROP = "host";

	/** @see #getIpv4() */
	private static final String IPV_4__PROP = "ipv4";

	/** @see #getIpv6() */
	private static final String IPV_6__PROP = "ipv6";

	/** @see #isPreferIPv4() */
	private static final String PREFER_IPV_4__PROP = "preferIPv4";

	/** @see #getRegistrar() */
	private static final String REGISTRAR__PROP = "registrar";

	/** @see #getRealm() */
	private static final String REALM__PROP = "realm";

	/** @see #getUserName() */
	private static final String USER_NAME__PROP = "userName";

	/** @see #getPasswd() */
	private static final String PASSWD__PROP = "passwd";

	/** @see #getMinVotes() */
	private static final String MIN_VOTES__PROP = "minVotes";

	/** @see #isWildcards() */
	private static final String WILDCARDS__PROP = "wildcards";

	/** @see #isAcceptLocal() */
	private static final String ACCEPT_LOCAL__PROP = "acceptLocal";

	/** @see #isRegistered() */
	private static final String REGISTERED__PROP = "registered";

	/** @see #getRegisterMessage() */
	private static final String REGISTER_MESSAGE__PROP = "registerMessage";

	/** @see #getLastSuccess() */
	private static final String LAST_SUCCESS__PROP = "lastSuccess";

	/** @see #getCallsAccepted() */
	private static final String CALLS_ACCEPTED__PROP = "callsAccepted";

	private String _host = "";

	private String _ipv4 = "";

	private String _ipv6 = "";

	private boolean _preferIPv4 = false;

	private String _registrar = "";

	private String _realm = "";

	private String _userName = "";

	private String _passwd = "";

	private int _minVotes = 0;

	private boolean _wildcards = false;

	private boolean _acceptLocal = false;

	private boolean _registered = false;

	private String _registerMessage = "";

	private long _lastSuccess = 0L;

	private int _callsAccepted = 0;

	/**
	 * Creates a {@link AnswerBotSip} instance.
	 *
	 * @see de.haumacher.phoneblock.db.settings.AnswerBotSip#create()
	 */
	protected AnswerBotSip() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.ANSWER_BOT_SIP;
	}

	/**
	 * Static host name configured for the user's box.
	 */
	public final String getHost() {
		return _host;
	}

	/**
	 * @see #getHost()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setHost(String value) {
		internalSetHost(value);
		return this;
	}

	/** Internal setter for {@link #getHost()} without chain call utility. */
	protected final void internalSetHost(String value) {
		_host = value;
	}

	/**
	 * IPv4 address transmitted during the last DynDNS update.
	 */
	public final String getIpv4() {
		return _ipv4;
	}

	/**
	 * @see #getIpv4()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setIpv4(String value) {
		internalSetIpv4(value);
		return this;
	}

	/** Internal setter for {@link #getIpv4()} without chain call utility. */
	protected final void internalSetIpv4(String value) {
		_ipv4 = value;
	}

	/**
	 * IPv6 address transmitted during the last DynDNS update.
	 */
	public final String getIpv6() {
		return _ipv6;
	}

	/**
	 * @see #getIpv6()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setIpv6(String value) {
		internalSetIpv6(value);
		return this;
	}

	/** Internal setter for {@link #getIpv6()} without chain call utility. */
	protected final void internalSetIpv6(String value) {
		_ipv6 = value;
	}

	/**
	 * Whether to use the IPv4 address for communication, even if an IPv6 address is available.
	 */
	public final boolean isPreferIPv4() {
		return _preferIPv4;
	}

	/**
	 * @see #isPreferIPv4()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setPreferIPv4(boolean value) {
		internalSetPreferIPv4(value);
		return this;
	}

	/** Internal setter for {@link #isPreferIPv4()} without chain call utility. */
	protected final void internalSetPreferIPv4(boolean value) {
		_preferIPv4 = value;
	}

	/**
	 * The SIP name of the user's box.
	 */
	public final String getRegistrar() {
		return _registrar;
	}

	/**
	 * @see #getRegistrar()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setRegistrar(String value) {
		internalSetRegistrar(value);
		return this;
	}

	/** Internal setter for {@link #getRegistrar()} without chain call utility. */
	protected final void internalSetRegistrar(String value) {
		_registrar = value;
	}

	/**
	 * The SIP domain for authentication at the user's box.
	 */
	public final String getRealm() {
		return _realm;
	}

	/**
	 * @see #getRealm()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setRealm(String value) {
		internalSetRealm(value);
		return this;
	}

	/** Internal setter for {@link #getRealm()} without chain call utility. */
	protected final void internalSetRealm(String value) {
		_realm = value;
	}

	/**
	 * The user name for SIP registration at the user's box.
	 */
	public final String getUserName() {
		return _userName;
	}

	/**
	 * @see #getUserName()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setUserName(String value) {
		internalSetUserName(value);
		return this;
	}

	/** Internal setter for {@link #getUserName()} without chain call utility. */
	protected final void internalSetUserName(String value) {
		_userName = value;
	}

	/**
	 * The password used for SIP registration at the user's box.
	 */
	public final String getPasswd() {
		return _passwd;
	}

	/**
	 * @see #getPasswd()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setPasswd(String value) {
		internalSetPasswd(value);
		return this;
	}

	/** Internal setter for {@link #getPasswd()} without chain call utility. */
	protected final void internalSetPasswd(String value) {
		_passwd = value;
	}

	/**
	 * The minimum PhoneBlock votes to consider a call as SPAM and accept it.
	 */
	public final int getMinVotes() {
		return _minVotes;
	}

	/**
	 * @see #getMinVotes()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setMinVotes(int value) {
		internalSetMinVotes(value);
		return this;
	}

	/** Internal setter for {@link #getMinVotes()} without chain call utility. */
	protected final void internalSetMinVotes(int value) {
		_minVotes = value;
	}

	/**
	 * Whether to block whole number ranges, when a great density of nearby SPAM numbers is detected.
	 */
	public final boolean isWildcards() {
		return _wildcards;
	}

	/**
	 * @see #isWildcards()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setWildcards(boolean value) {
		internalSetWildcards(value);
		return this;
	}

	/** Internal setter for {@link #isWildcards()} without chain call utility. */
	protected final void internalSetWildcards(boolean value) {
		_wildcards = value;
	}

	/**
	 * Whether to accept calls from local phones (numbers starting with *).
	 */
	public final boolean isAcceptLocal() {
		return _acceptLocal;
	}

	/**
	 * @see #isAcceptLocal()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setAcceptLocal(boolean value) {
		internalSetAcceptLocal(value);
		return this;
	}

	/** Internal setter for {@link #isAcceptLocal()} without chain call utility. */
	protected final void internalSetAcceptLocal(boolean value) {
		_acceptLocal = value;
	}

	/**
	 * Whether this answer bot is currently registered successfully.
	 */
	public final boolean isRegistered() {
		return _registered;
	}

	/**
	 * @see #isRegistered()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setRegistered(boolean value) {
		internalSetRegistered(value);
		return this;
	}

	/** Internal setter for {@link #isRegistered()} without chain call utility. */
	protected final void internalSetRegistered(boolean value) {
		_registered = value;
	}

	/**
	 * The last message transmitted while registering.
	 */
	public final String getRegisterMessage() {
		return _registerMessage;
	}

	/**
	 * @see #getRegisterMessage()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setRegisterMessage(String value) {
		internalSetRegisterMessage(value);
		return this;
	}

	/** Internal setter for {@link #getRegisterMessage()} without chain call utility. */
	protected final void internalSetRegisterMessage(String value) {
		_registerMessage = value;
	}

	/**
	 * Time of the last successful registration.
	 */
	public final long getLastSuccess() {
		return _lastSuccess;
	}

	/**
	 * @see #getLastSuccess()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setLastSuccess(long value) {
		internalSetLastSuccess(value);
		return this;
	}

	/** Internal setter for {@link #getLastSuccess()} without chain call utility. */
	protected final void internalSetLastSuccess(long value) {
		_lastSuccess = value;
	}

	/**
	 * The number of calls accepted by this answer bot.
	 */
	public final int getCallsAccepted() {
		return _callsAccepted;
	}

	/**
	 * @see #getCallsAccepted()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setCallsAccepted(int value) {
		internalSetCallsAccepted(value);
		return this;
	}

	/** Internal setter for {@link #getCallsAccepted()} without chain call utility. */
	protected final void internalSetCallsAccepted(int value) {
		_callsAccepted = value;
	}

	@Override
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setUserId(long value) {
		internalSetUserId(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setCreated(long value) {
		internalSetCreated(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setUpdated(long value) {
		internalSetUpdated(value);
		return this;
	}

	@Override
	public String jsonType() {
		return ANSWER_BOT_SIP__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.AnswerBotSip readAnswerBotSip(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.AnswerBotSip result = new de.haumacher.phoneblock.db.settings.AnswerBotSip();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(HOST__PROP);
		out.value(getHost());
		out.name(IPV_4__PROP);
		out.value(getIpv4());
		out.name(IPV_6__PROP);
		out.value(getIpv6());
		out.name(PREFER_IPV_4__PROP);
		out.value(isPreferIPv4());
		out.name(REGISTRAR__PROP);
		out.value(getRegistrar());
		out.name(REALM__PROP);
		out.value(getRealm());
		out.name(USER_NAME__PROP);
		out.value(getUserName());
		out.name(PASSWD__PROP);
		out.value(getPasswd());
		out.name(MIN_VOTES__PROP);
		out.value(getMinVotes());
		out.name(WILDCARDS__PROP);
		out.value(isWildcards());
		out.name(ACCEPT_LOCAL__PROP);
		out.value(isAcceptLocal());
		out.name(REGISTERED__PROP);
		out.value(isRegistered());
		out.name(REGISTER_MESSAGE__PROP);
		out.value(getRegisterMessage());
		out.name(LAST_SUCCESS__PROP);
		out.value(getLastSuccess());
		out.name(CALLS_ACCEPTED__PROP);
		out.value(getCallsAccepted());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case HOST__PROP: setHost(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case IPV_4__PROP: setIpv4(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case IPV_6__PROP: setIpv6(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PREFER_IPV_4__PROP: setPreferIPv4(in.nextBoolean()); break;
			case REGISTRAR__PROP: setRegistrar(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case REALM__PROP: setRealm(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case USER_NAME__PROP: setUserName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PASSWD__PROP: setPasswd(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case MIN_VOTES__PROP: setMinVotes(in.nextInt()); break;
			case WILDCARDS__PROP: setWildcards(in.nextBoolean()); break;
			case ACCEPT_LOCAL__PROP: setAcceptLocal(in.nextBoolean()); break;
			case REGISTERED__PROP: setRegistered(in.nextBoolean()); break;
			case REGISTER_MESSAGE__PROP: setRegisterMessage(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LAST_SUCCESS__PROP: setLastSuccess(in.nextLong()); break;
			case CALLS_ACCEPTED__PROP: setCallsAccepted(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.db.settings.AnswerBotSetting.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
