package de.haumacher.phoneblock.ab.proto;

public class CreateAnswerbotResponse extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse create() {
		return new de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse} type in JSON format. */
	public static final String CREATE_ANSWERBOT_RESPONSE__TYPE = "CreateAnswerbotResponse";

	/** @see #getId() */
	public static final String ID__PROP = "id";

	/** @see #getUserName() */
	public static final String USER_NAME__PROP = "userName";

	/** @see #getPassword() */
	public static final String PASSWORD__PROP = "password";

	private long _id = 0L;

	private String _userName = "";

	private String _password = "";

	/**
	 * Creates a {@link CreateAnswerbotResponse} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse#create()
	 */
	protected CreateAnswerbotResponse() {
		super();
	}

	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_id = value;
	}

	public final String getUserName() {
		return _userName;
	}

	/**
	 * @see #getUserName()
	 */
	public de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse setUserName(String value) {
		internalSetUserName(value);
		return this;
	}

	/** Internal setter for {@link #getUserName()} without chain call utility. */
	protected final void internalSetUserName(String value) {
		_userName = value;
	}

	public final String getPassword() {
		return _password;
	}

	/**
	 * @see #getPassword()
	 */
	public de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse setPassword(String value) {
		internalSetPassword(value);
		return this;
	}

	/** Internal setter for {@link #getPassword()} without chain call utility. */
	protected final void internalSetPassword(String value) {
		_password = value;
	}

	@Override
	public String jsonType() {
		return CREATE_ANSWERBOT_RESPONSE__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ID__PROP, 
			USER_NAME__PROP, 
			PASSWORD__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ID__PROP: return getId();
			case USER_NAME__PROP: return getUserName();
			case PASSWORD__PROP: return getPassword();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ID__PROP: internalSetId((long) value); break;
			case USER_NAME__PROP: internalSetUserName((String) value); break;
			case PASSWORD__PROP: internalSetPassword((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse readCreateAnswerbotResponse(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse result = new de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse();
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
		out.name(USER_NAME__PROP);
		out.value(getUserName());
		out.name(PASSWORD__PROP);
		out.value(getPassword());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(in.nextLong()); break;
			case USER_NAME__PROP: setUserName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PASSWORD__PROP: setPassword(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

}
