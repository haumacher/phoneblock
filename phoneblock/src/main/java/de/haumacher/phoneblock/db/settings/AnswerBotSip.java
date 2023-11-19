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

	/** @see #getSipRegistrar() */
	public static final String SIP_REGISTRAR__PROP = "sipRegistrar";

	/** @see #getSipDomain() */
	public static final String SIP_DOMAIN__PROP = "sipDomain";

	/** @see #getSipUser() */
	public static final String SIP_USER__PROP = "sipUser";

	/** @see #getSipPasswd() */
	public static final String SIP_PASSWD__PROP = "sipPasswd";

	/** @see #isRegistered() */
	public static final String REGISTERED__PROP = "registered";

	/** @see #getLastRegister() */
	public static final String LAST_REGISTER__PROP = "lastRegister";

	/** @see #getRegisterError() */
	public static final String REGISTER_ERROR__PROP = "registerError";

	/** @see #getCallsAccepted() */
	public static final String CALLS_ACCEPTED__PROP = "callsAccepted";

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.AnswerBotSip} type in binary format. */
	static final int ANSWER_BOT_SIP__TYPE_ID = 2;

	/** Identifier for the property {@link #getHost()} in binary format. */
	static final int HOST__ID = 4;

	/** Identifier for the property {@link #getSipRegistrar()} in binary format. */
	static final int SIP_REGISTRAR__ID = 5;

	/** Identifier for the property {@link #getSipDomain()} in binary format. */
	static final int SIP_DOMAIN__ID = 6;

	/** Identifier for the property {@link #getSipUser()} in binary format. */
	static final int SIP_USER__ID = 7;

	/** Identifier for the property {@link #getSipPasswd()} in binary format. */
	static final int SIP_PASSWD__ID = 8;

	/** Identifier for the property {@link #isRegistered()} in binary format. */
	static final int REGISTERED__ID = 9;

	/** Identifier for the property {@link #getLastRegister()} in binary format. */
	static final int LAST_REGISTER__ID = 10;

	/** Identifier for the property {@link #getRegisterError()} in binary format. */
	static final int REGISTER_ERROR__ID = 11;

	/** Identifier for the property {@link #getCallsAccepted()} in binary format. */
	static final int CALLS_ACCEPTED__ID = 12;

	private String _host = "";

	private String _sipRegistrar = "";

	private String _sipDomain = "";

	private String _sipUser = "";

	private String _sipPasswd = "";

	private boolean _registered = false;

	private long _lastRegister = 0L;

	private String _registerError = "";

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
	 * The SIP name of the user's box.
	 */
	public final String getSipRegistrar() {
		return _sipRegistrar;
	}

	/**
	 * @see #getSipRegistrar()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setSipRegistrar(String value) {
		internalSetSipRegistrar(value);
		return this;
	}

	/** Internal setter for {@link #getSipRegistrar()} without chain call utility. */
	protected final void internalSetSipRegistrar(String value) {
		_listener.beforeSet(this, SIP_REGISTRAR__PROP, value);
		_sipRegistrar = value;
	}

	/**
	 * The SIP domain for authentication at the user's box.
	 */
	public final String getSipDomain() {
		return _sipDomain;
	}

	/**
	 * @see #getSipDomain()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setSipDomain(String value) {
		internalSetSipDomain(value);
		return this;
	}

	/** Internal setter for {@link #getSipDomain()} without chain call utility. */
	protected final void internalSetSipDomain(String value) {
		_listener.beforeSet(this, SIP_DOMAIN__PROP, value);
		_sipDomain = value;
	}

	/**
	 * The user name for SIP registration at the user's box.
	 */
	public final String getSipUser() {
		return _sipUser;
	}

	/**
	 * @see #getSipUser()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setSipUser(String value) {
		internalSetSipUser(value);
		return this;
	}

	/** Internal setter for {@link #getSipUser()} without chain call utility. */
	protected final void internalSetSipUser(String value) {
		_listener.beforeSet(this, SIP_USER__PROP, value);
		_sipUser = value;
	}

	/**
	 * The password used for SIP registration at the user's box.
	 */
	public final String getSipPasswd() {
		return _sipPasswd;
	}

	/**
	 * @see #getSipPasswd()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setSipPasswd(String value) {
		internalSetSipPasswd(value);
		return this;
	}

	/** Internal setter for {@link #getSipPasswd()} without chain call utility. */
	protected final void internalSetSipPasswd(String value) {
		_listener.beforeSet(this, SIP_PASSWD__PROP, value);
		_sipPasswd = value;
	}

	/**
	 * Whether this answer bot is currently registered sucessfully.
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
	 * Whether the last registration process finished.
	 */
	public final long getLastRegister() {
		return _lastRegister;
	}

	/**
	 * @see #getLastRegister()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setLastRegister(long value) {
		internalSetLastRegister(value);
		return this;
	}

	/** Internal setter for {@link #getLastRegister()} without chain call utility. */
	protected final void internalSetLastRegister(long value) {
		_listener.beforeSet(this, LAST_REGISTER__PROP, value);
		_lastRegister = value;
	}

	/**
	 * An error message transmitted during the last registration process.
	 */
	public final String getRegisterError() {
		return _registerError;
	}

	/**
	 * @see #getRegisterError()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSip setRegisterError(String value) {
		internalSetRegisterError(value);
		return this;
	}

	/** Internal setter for {@link #getRegisterError()} without chain call utility. */
	protected final void internalSetRegisterError(String value) {
		_listener.beforeSet(this, REGISTER_ERROR__PROP, value);
		_registerError = value;
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
			SIP_REGISTRAR__PROP, 
			SIP_DOMAIN__PROP, 
			SIP_USER__PROP, 
			SIP_PASSWD__PROP, 
			REGISTERED__PROP, 
			LAST_REGISTER__PROP, 
			REGISTER_ERROR__PROP, 
			CALLS_ACCEPTED__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case HOST__PROP: return getHost();
			case SIP_REGISTRAR__PROP: return getSipRegistrar();
			case SIP_DOMAIN__PROP: return getSipDomain();
			case SIP_USER__PROP: return getSipUser();
			case SIP_PASSWD__PROP: return getSipPasswd();
			case REGISTERED__PROP: return isRegistered();
			case LAST_REGISTER__PROP: return getLastRegister();
			case REGISTER_ERROR__PROP: return getRegisterError();
			case CALLS_ACCEPTED__PROP: return getCallsAccepted();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case HOST__PROP: internalSetHost((String) value); break;
			case SIP_REGISTRAR__PROP: internalSetSipRegistrar((String) value); break;
			case SIP_DOMAIN__PROP: internalSetSipDomain((String) value); break;
			case SIP_USER__PROP: internalSetSipUser((String) value); break;
			case SIP_PASSWD__PROP: internalSetSipPasswd((String) value); break;
			case REGISTERED__PROP: internalSetRegistered((boolean) value); break;
			case LAST_REGISTER__PROP: internalSetLastRegister((long) value); break;
			case REGISTER_ERROR__PROP: internalSetRegisterError((String) value); break;
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
		out.name(SIP_REGISTRAR__PROP);
		out.value(getSipRegistrar());
		out.name(SIP_DOMAIN__PROP);
		out.value(getSipDomain());
		out.name(SIP_USER__PROP);
		out.value(getSipUser());
		out.name(SIP_PASSWD__PROP);
		out.value(getSipPasswd());
		out.name(REGISTERED__PROP);
		out.value(isRegistered());
		out.name(LAST_REGISTER__PROP);
		out.value(getLastRegister());
		out.name(REGISTER_ERROR__PROP);
		out.value(getRegisterError());
		out.name(CALLS_ACCEPTED__PROP);
		out.value(getCallsAccepted());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case HOST__PROP: setHost(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case SIP_REGISTRAR__PROP: setSipRegistrar(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case SIP_DOMAIN__PROP: setSipDomain(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case SIP_USER__PROP: setSipUser(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case SIP_PASSWD__PROP: setSipPasswd(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case REGISTERED__PROP: setRegistered(in.nextBoolean()); break;
			case LAST_REGISTER__PROP: setLastRegister(in.nextLong()); break;
			case REGISTER_ERROR__PROP: setRegisterError(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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
		out.name(SIP_REGISTRAR__ID);
		out.value(getSipRegistrar());
		out.name(SIP_DOMAIN__ID);
		out.value(getSipDomain());
		out.name(SIP_USER__ID);
		out.value(getSipUser());
		out.name(SIP_PASSWD__ID);
		out.value(getSipPasswd());
		out.name(REGISTERED__ID);
		out.value(isRegistered());
		out.name(LAST_REGISTER__ID);
		out.value(getLastRegister());
		out.name(REGISTER_ERROR__ID);
		out.value(getRegisterError());
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
			case SIP_REGISTRAR__ID: setSipRegistrar(in.nextString()); break;
			case SIP_DOMAIN__ID: setSipDomain(in.nextString()); break;
			case SIP_USER__ID: setSipUser(in.nextString()); break;
			case SIP_PASSWD__ID: setSipPasswd(in.nextString()); break;
			case REGISTERED__ID: setRegistered(in.nextBoolean()); break;
			case LAST_REGISTER__ID: setLastRegister(in.nextLong()); break;
			case REGISTER_ERROR__ID: setRegisterError(in.nextString()); break;
			case CALLS_ACCEPTED__ID: setCallsAccepted(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.db.settings.AnswerBotSetting.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
