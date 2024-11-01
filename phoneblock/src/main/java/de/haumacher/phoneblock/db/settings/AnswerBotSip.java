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
	public static final String HOST__PROP = "host";

	/** @see #getIpv4() */
	public static final String IPV_4__PROP = "ipv4";

	/** @see #getIpv6() */
	public static final String IPV_6__PROP = "ipv6";

	/** @see #getRegistrar() */
	public static final String REGISTRAR__PROP = "registrar";

	/** @see #getRealm() */
	public static final String REALM__PROP = "realm";

	/** @see #getUserName() */
	public static final String USER_NAME__PROP = "userName";

	/** @see #getPasswd() */
	public static final String PASSWD__PROP = "passwd";

	/** @see #isRegistered() */
	public static final String REGISTERED__PROP = "registered";

	/** @see #getRegisterMessage() */
	public static final String REGISTER_MESSAGE__PROP = "registerMessage";

	/** @see #getLastSuccess() */
	public static final String LAST_SUCCESS__PROP = "lastSuccess";

	/** @see #getCallsAccepted() */
	public static final String CALLS_ACCEPTED__PROP = "callsAccepted";

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.AnswerBotSip} type in binary format. */
	static final int ANSWER_BOT_SIP__TYPE_ID = 2;

	/** Identifier for the property {@link #getHost()} in binary format. */
	static final int HOST__ID = 5;

	/** Identifier for the property {@link #getIpv4()} in binary format. */
	static final int IPV_4__ID = 6;

	/** Identifier for the property {@link #getIpv6()} in binary format. */
	static final int IPV_6__ID = 7;

	/** Identifier for the property {@link #getRegistrar()} in binary format. */
	static final int REGISTRAR__ID = 8;

	/** Identifier for the property {@link #getRealm()} in binary format. */
	static final int REALM__ID = 9;

	/** Identifier for the property {@link #getUserName()} in binary format. */
	static final int USER_NAME__ID = 10;

	/** Identifier for the property {@link #getPasswd()} in binary format. */
	static final int PASSWD__ID = 11;

	/** Identifier for the property {@link #isRegistered()} in binary format. */
	static final int REGISTERED__ID = 12;

	/** Identifier for the property {@link #getRegisterMessage()} in binary format. */
	static final int REGISTER_MESSAGE__ID = 13;

	/** Identifier for the property {@link #getLastSuccess()} in binary format. */
	static final int LAST_SUCCESS__ID = 14;

	/** Identifier for the property {@link #getCallsAccepted()} in binary format. */
	static final int CALLS_ACCEPTED__ID = 15;

	private String _host = "";

	private String _ipv4 = "";

	private String _ipv6 = "";

	private String _registrar = "";

	private String _realm = "";

	private String _userName = "";

	private String _passwd = "";

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
		_listener.beforeSet(this, HOST__PROP, value);
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
		_listener.beforeSet(this, IPV_4__PROP, value);
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
		_listener.beforeSet(this, IPV_6__PROP, value);
		_ipv6 = value;
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
		_listener.beforeSet(this, REGISTRAR__PROP, value);
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
		_listener.beforeSet(this, REALM__PROP, value);
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
		_listener.beforeSet(this, USER_NAME__PROP, value);
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
		_listener.beforeSet(this, PASSWD__PROP, value);
		_passwd = value;
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
		_listener.beforeSet(this, REGISTERED__PROP, value);
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
		_listener.beforeSet(this, REGISTER_MESSAGE__PROP, value);
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
		_listener.beforeSet(this, LAST_SUCCESS__PROP, value);
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
		_listener.beforeSet(this, CALLS_ACCEPTED__PROP, value);
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

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			HOST__PROP, 
			IPV_4__PROP, 
			IPV_6__PROP, 
			REGISTRAR__PROP, 
			REALM__PROP, 
			USER_NAME__PROP, 
			PASSWD__PROP, 
			REGISTERED__PROP, 
			REGISTER_MESSAGE__PROP, 
			LAST_SUCCESS__PROP, 
			CALLS_ACCEPTED__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case HOST__PROP: return getHost();
			case IPV_4__PROP: return getIpv4();
			case IPV_6__PROP: return getIpv6();
			case REGISTRAR__PROP: return getRegistrar();
			case REALM__PROP: return getRealm();
			case USER_NAME__PROP: return getUserName();
			case PASSWD__PROP: return getPasswd();
			case REGISTERED__PROP: return isRegistered();
			case REGISTER_MESSAGE__PROP: return getRegisterMessage();
			case LAST_SUCCESS__PROP: return getLastSuccess();
			case CALLS_ACCEPTED__PROP: return getCallsAccepted();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case HOST__PROP: internalSetHost((String) value); break;
			case IPV_4__PROP: internalSetIpv4((String) value); break;
			case IPV_6__PROP: internalSetIpv6((String) value); break;
			case REGISTRAR__PROP: internalSetRegistrar((String) value); break;
			case REALM__PROP: internalSetRealm((String) value); break;
			case USER_NAME__PROP: internalSetUserName((String) value); break;
			case PASSWD__PROP: internalSetPasswd((String) value); break;
			case REGISTERED__PROP: internalSetRegistered((boolean) value); break;
			case REGISTER_MESSAGE__PROP: internalSetRegisterMessage((String) value); break;
			case LAST_SUCCESS__PROP: internalSetLastSuccess((long) value); break;
			case CALLS_ACCEPTED__PROP: internalSetCallsAccepted((int) value); break;
			default: super.set(field, value); break;
		}
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
		out.name(REGISTRAR__PROP);
		out.value(getRegistrar());
		out.name(REALM__PROP);
		out.value(getRealm());
		out.name(USER_NAME__PROP);
		out.value(getUserName());
		out.name(PASSWD__PROP);
		out.value(getPasswd());
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
			case REGISTRAR__PROP: setRegistrar(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case REALM__PROP: setRealm(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case USER_NAME__PROP: setUserName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PASSWD__PROP: setPasswd(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case REGISTERED__PROP: setRegistered(in.nextBoolean()); break;
			case REGISTER_MESSAGE__PROP: setRegisterMessage(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LAST_SUCCESS__PROP: setLastSuccess(in.nextLong()); break;
			case CALLS_ACCEPTED__PROP: setCallsAccepted(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	/** The binary identifier for this concrete type in the polymorphic {@link de.haumacher.phoneblock.db.settings.AnswerBotSip} hierarchy. */
	public int typeId() {
		return ANSWER_BOT_SIP__TYPE_ID;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(HOST__ID);
		out.value(getHost());
		out.name(IPV_4__ID);
		out.value(getIpv4());
		out.name(IPV_6__ID);
		out.value(getIpv6());
		out.name(REGISTRAR__ID);
		out.value(getRegistrar());
		out.name(REALM__ID);
		out.value(getRealm());
		out.name(USER_NAME__ID);
		out.value(getUserName());
		out.name(PASSWD__ID);
		out.value(getPasswd());
		out.name(REGISTERED__ID);
		out.value(isRegistered());
		out.name(REGISTER_MESSAGE__ID);
		out.value(getRegisterMessage());
		out.name(LAST_SUCCESS__ID);
		out.value(getLastSuccess());
		out.name(CALLS_ACCEPTED__ID);
		out.value(getCallsAccepted());
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.AnswerBotSip readAnswerBotSip(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.db.settings.AnswerBotSip result = de.haumacher.phoneblock.db.settings.AnswerBotSip.readAnswerBotSip_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.db.settings.AnswerBotSip} from a polymorphic composition. */
	public static de.haumacher.phoneblock.db.settings.AnswerBotSip readAnswerBotSip_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.AnswerBotSip result = new AnswerBotSip();
		result.readContent(in);
		return result;
	}

	@Override
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case HOST__ID: setHost(in.nextString()); break;
			case IPV_4__ID: setIpv4(in.nextString()); break;
			case IPV_6__ID: setIpv6(in.nextString()); break;
			case REGISTRAR__ID: setRegistrar(in.nextString()); break;
			case REALM__ID: setRealm(in.nextString()); break;
			case USER_NAME__ID: setUserName(in.nextString()); break;
			case PASSWD__ID: setPasswd(in.nextString()); break;
			case REGISTERED__ID: setRegistered(in.nextBoolean()); break;
			case REGISTER_MESSAGE__ID: setRegisterMessage(in.nextString()); break;
			case LAST_SUCCESS__ID: setLastSuccess(in.nextLong()); break;
			case CALLS_ACCEPTED__ID: setCallsAccepted(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.db.settings.AnswerBotSetting.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
