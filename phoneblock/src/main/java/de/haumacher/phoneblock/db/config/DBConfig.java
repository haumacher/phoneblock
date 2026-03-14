package de.haumacher.phoneblock.db.config;

/**
 * JNDI configuration settings for the embedded H2 database
 */
public class DBConfig extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.config.DBConfig} instance.
	 */
	public static de.haumacher.phoneblock.db.config.DBConfig create() {
		return new de.haumacher.phoneblock.db.config.DBConfig();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.config.DBConfig} type in JSON format. */
	public static final String DBCONFIG__TYPE = "DBConfig";

	/** @see #getUrl() */
	public static final String URL__PROP = "url";

	/** @see #getUser() */
	public static final String USER__PROP = "user";

	/** @see #getPassword() */
	public static final String PASSWORD__PROP = "password";

	/** @see #getPort() */
	public static final String PORT__PROP = "port";

	/** @see #isSendHelpMails() */
	public static final String SEND_HELP_MAILS__PROP = "sendHelpMails";

	/** @see #isSendWelcomeMails() */
	public static final String SEND_WELCOME_MAILS__PROP = "sendWelcomeMails";

	private String _url = "";

	private String _user = "";

	private String _password = "";

	private int _port = 0;

	private boolean _sendHelpMails = false;

	private boolean _sendWelcomeMails = false;

	/**
	 * Creates a {@link DBConfig} instance.
	 *
	 * @see de.haumacher.phoneblock.db.config.DBConfig#create()
	 */
	protected DBConfig() {
		super();
	}

	/**
	 * The DB url, where the file is located.
	 */
	public final String getUrl() {
		return _url;
	}

	/**
	 * @see #getUrl()
	 */
	public de.haumacher.phoneblock.db.config.DBConfig setUrl(String value) {
		internalSetUrl(value);
		return this;
	}

	/** Internal setter for {@link #getUrl()} without chain call utility. */
	protected final void internalSetUrl(String value) {
		_url = value;
	}

	/**
	 * The DB user to access the database.
	 */
	public final String getUser() {
		return _user;
	}

	/**
	 * @see #getUser()
	 */
	public de.haumacher.phoneblock.db.config.DBConfig setUser(String value) {
		internalSetUser(value);
		return this;
	}

	/** Internal setter for {@link #getUser()} without chain call utility. */
	protected final void internalSetUser(String value) {
		_user = value;
	}

	/**
	 * The DB password to access the database.
	 */
	public final String getPassword() {
		return _password;
	}

	/**
	 * @see #getPassword()
	 */
	public de.haumacher.phoneblock.db.config.DBConfig setPassword(String value) {
		internalSetPassword(value);
		return this;
	}

	/** Internal setter for {@link #getPassword()} without chain call utility. */
	protected final void internalSetPassword(String value) {
		_password = value;
	}

	/**
	 * The port, where to start a server for external access. <code>0</code> to prevent starting a DB server.
	 */
	public final int getPort() {
		return _port;
	}

	/**
	 * @see #getPort()
	 */
	public de.haumacher.phoneblock.db.config.DBConfig setPort(int value) {
		internalSetPort(value);
		return this;
	}

	/** Internal setter for {@link #getPort()} without chain call utility. */
	protected final void internalSetPort(int value) {
		_port = value;
	}

	/**
	 * Whether to automatically send help mails when a period of inactivity is detected.
	 */
	public final boolean isSendHelpMails() {
		return _sendHelpMails;
	}

	/**
	 * @see #isSendHelpMails()
	 */
	public de.haumacher.phoneblock.db.config.DBConfig setSendHelpMails(boolean value) {
		internalSetSendHelpMails(value);
		return this;
	}

	/** Internal setter for {@link #isSendHelpMails()} without chain call utility. */
	protected final void internalSetSendHelpMails(boolean value) {
		_sendHelpMails = value;
	}

	/**
	 * Whether to send welcome mails after the blocklist has been synchronized the first time.
	 */
	public final boolean isSendWelcomeMails() {
		return _sendWelcomeMails;
	}

	/**
	 * @see #isSendWelcomeMails()
	 */
	public de.haumacher.phoneblock.db.config.DBConfig setSendWelcomeMails(boolean value) {
		internalSetSendWelcomeMails(value);
		return this;
	}

	/** Internal setter for {@link #isSendWelcomeMails()} without chain call utility. */
	protected final void internalSetSendWelcomeMails(boolean value) {
		_sendWelcomeMails = value;
	}

	@Override
	public String jsonType() {
		return DBCONFIG__TYPE;
	}

	static final java.util.List<String> PROPERTIES;
	static {
		java.util.List<String> local = java.util.Arrays.asList(
			URL__PROP, 
			USER__PROP, 
			PASSWORD__PROP, 
			PORT__PROP, 
			SEND_HELP_MAILS__PROP, 
			SEND_WELCOME_MAILS__PROP);
		PROPERTIES = java.util.Collections.unmodifiableList(local);
	}

	static final java.util.Set<String> TRANSIENT_PROPERTIES;
	static {
		java.util.HashSet<String> tmp = new java.util.HashSet<>();
		tmp.addAll(java.util.Arrays.asList(
				));
		TRANSIENT_PROPERTIES = java.util.Collections.unmodifiableSet(tmp);
	}

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public java.util.Set<String> transientProperties() {
		return TRANSIENT_PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case URL__PROP: return getUrl();
			case USER__PROP: return getUser();
			case PASSWORD__PROP: return getPassword();
			case PORT__PROP: return getPort();
			case SEND_HELP_MAILS__PROP: return isSendHelpMails();
			case SEND_WELCOME_MAILS__PROP: return isSendWelcomeMails();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case URL__PROP: internalSetUrl((String) value); break;
			case USER__PROP: internalSetUser((String) value); break;
			case PASSWORD__PROP: internalSetPassword((String) value); break;
			case PORT__PROP: internalSetPort((int) value); break;
			case SEND_HELP_MAILS__PROP: internalSetSendHelpMails((boolean) value); break;
			case SEND_WELCOME_MAILS__PROP: internalSetSendWelcomeMails((boolean) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.config.DBConfig readDBConfig(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.config.DBConfig result = new de.haumacher.phoneblock.db.config.DBConfig();
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
		out.name(URL__PROP);
		out.value(getUrl());
		out.name(USER__PROP);
		out.value(getUser());
		out.name(PASSWORD__PROP);
		out.value(getPassword());
		out.name(PORT__PROP);
		out.value(getPort());
		out.name(SEND_HELP_MAILS__PROP);
		out.value(isSendHelpMails());
		out.name(SEND_WELCOME_MAILS__PROP);
		out.value(isSendWelcomeMails());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case URL__PROP: setUrl(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case USER__PROP: setUser(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PASSWORD__PROP: setPassword(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PORT__PROP: setPort(in.nextInt()); break;
			case SEND_HELP_MAILS__PROP: setSendHelpMails(in.nextBoolean()); break;
			case SEND_WELCOME_MAILS__PROP: setSendWelcomeMails(in.nextBoolean()); break;
			default: super.readField(in, field);
		}
	}

}
