package de.haumacher.phoneblock.db.config;

/**
 * Configuration settings for the embedded H2 database
 */
public class DBConfig extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link DBConfig} instance.
	 */
	public static DBConfig create() {
		return new DBConfig();
	}

	/** Identifier for the {@link DBConfig} type in JSON format. */
	public static final String DBCONFIG__TYPE = "DBConfig";

	/** @see #getUrl() */
	public static final String URL = "url";

	/** @see #getUser() */
	public static final String USER = "user";

	/** @see #getPassword() */
	public static final String PASSWORD = "password";

	/** @see #getPort() */
	public static final String PORT = "port";

	/** Identifier for the property {@link #getUrl()} in binary format. */
	public static final int URL__ID = 1;

	/** Identifier for the property {@link #getUser()} in binary format. */
	public static final int USER__ID = 2;

	/** Identifier for the property {@link #getPassword()} in binary format. */
	public static final int PASSWORD__ID = 3;

	/** Identifier for the property {@link #getPort()} in binary format. */
	public static final int PORT__ID = 4;

	private String _url = "";

	private String _user = "";

	private String _password = "";

	private int _port = 0;

	/**
	 * Creates a {@link DBConfig} instance.
	 *
	 * @see #create()
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
	public DBConfig setUrl(String value) {
		internalSetUrl(value);
		return this;
	}
	/** Internal setter for {@link #getUrl()} without chain call utility. */
	protected final void internalSetUrl(String value) {
		_listener.beforeSet(this, URL, value);
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
	public DBConfig setUser(String value) {
		internalSetUser(value);
		return this;
	}
	/** Internal setter for {@link #getUser()} without chain call utility. */
	protected final void internalSetUser(String value) {
		_listener.beforeSet(this, USER, value);
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
	public DBConfig setPassword(String value) {
		internalSetPassword(value);
		return this;
	}
	/** Internal setter for {@link #getPassword()} without chain call utility. */
	protected final void internalSetPassword(String value) {
		_listener.beforeSet(this, PASSWORD, value);
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
	public DBConfig setPort(int value) {
		internalSetPort(value);
		return this;
	}
	/** Internal setter for {@link #getPort()} without chain call utility. */
	protected final void internalSetPort(int value) {
		_listener.beforeSet(this, PORT, value);
		_port = value;
	}


	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public DBConfig registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public DBConfig unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
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
			URL, 
			USER, 
			PASSWORD, 
			PORT));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case URL: return getUrl();
			case USER: return getUser();
			case PASSWORD: return getPassword();
			case PORT: return getPort();
			default: return de.haumacher.msgbuf.observer.Observable.super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case URL: setUrl((String) value); break;
			case USER: setUser((String) value); break;
			case PASSWORD: setPassword((String) value); break;
			case PORT: setPort((int) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static DBConfig readDBConfig(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		DBConfig result = new DBConfig();
		in.beginObject();
		result.readFields(in);
		in.endObject();
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		writeContent(out);
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(URL);
		out.value(getUrl());
		out.name(USER);
		out.value(getUser());
		out.name(PASSWORD);
		out.value(getPassword());
		out.name(PORT);
		out.value(getPort());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case URL: setUrl(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case USER: setUser(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PASSWORD: setPassword(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PORT: setPort(in.nextInt()); break;
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
	}

	/** Reads a new instance from the given reader. */
	public static DBConfig readDBConfig(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		DBConfig result = new DBConfig();
		while (in.hasNext()) {
			int field = in.nextName();
			result.readField(in, field);
		}
		in.endObject();
		return result;
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case URL__ID: setUrl(in.nextString()); break;
			case USER__ID: setUser(in.nextString()); break;
			case PASSWORD__ID: setPassword(in.nextString()); break;
			case PORT__ID: setPort(in.nextInt()); break;
			default: in.skipValue(); 
		}
	}

}
