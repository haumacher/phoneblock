package de.haumacher.phoneblock.db.config;

/**
 * JNDI configuration settings for the embedded H2 database
 */
public class DBConfig extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

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

	/** Identifier for the property {@link #getUrl()} in binary format. */
	static final int URL__ID = 1;

	/** Identifier for the property {@link #getUser()} in binary format. */
	static final int USER__ID = 2;

	/** Identifier for the property {@link #getPassword()} in binary format. */
	static final int PASSWORD__ID = 3;

	/** Identifier for the property {@link #getPort()} in binary format. */
	static final int PORT__ID = 4;

	/** Identifier for the property {@link #isSendHelpMails()} in binary format. */
	static final int SEND_HELP_MAILS__ID = 5;

	private String _url = "";

	private String _user = "";

	private String _password = "";

	private int _port = 0;

	private boolean _sendHelpMails = false;

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
		_listener.beforeSet(this, URL__PROP, value);
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
		_listener.beforeSet(this, USER__PROP, value);
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
		_listener.beforeSet(this, PASSWORD__PROP, value);
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
		_listener.beforeSet(this, PORT__PROP, value);
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
		_listener.beforeSet(this, SEND_HELP_MAILS__PROP, value);
		_sendHelpMails = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.config.DBConfig registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.config.DBConfig unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return DBCONFIG__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			URL__PROP, 
			USER__PROP, 
			PASSWORD__PROP, 
			PORT__PROP, 
			SEND_HELP_MAILS__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case URL__PROP: return getUrl();
			case USER__PROP: return getUser();
			case PASSWORD__PROP: return getPassword();
			case PORT__PROP: return getPort();
			case SEND_HELP_MAILS__PROP: return isSendHelpMails();
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
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case URL__PROP: setUrl(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case USER__PROP: setUser(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PASSWORD__PROP: setPassword(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PORT__PROP: setPort(in.nextInt()); break;
			case SEND_HELP_MAILS__PROP: setSendHelpMails(in.nextBoolean()); break;
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
		out.name(URL__ID);
		out.value(getUrl());
		out.name(USER__ID);
		out.value(getUser());
		out.name(PASSWORD__ID);
		out.value(getPassword());
		out.name(PORT__ID);
		out.value(getPort());
		out.name(SEND_HELP_MAILS__ID);
		out.value(isSendHelpMails());
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.config.DBConfig readDBConfig(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.db.config.DBConfig result = de.haumacher.phoneblock.db.config.DBConfig.readDBConfig_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.db.config.DBConfig} from a polymorphic composition. */
	public static de.haumacher.phoneblock.db.config.DBConfig readDBConfig_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.config.DBConfig result = new DBConfig();
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
			case URL__ID: setUrl(in.nextString()); break;
			case USER__ID: setUser(in.nextString()); break;
			case PASSWORD__ID: setPassword(in.nextString()); break;
			case PORT__ID: setPort(in.nextInt()); break;
			case SEND_HELP_MAILS__ID: setSendHelpMails(in.nextBoolean()); break;
			default: in.skipValue(); 
		}
	}

}
