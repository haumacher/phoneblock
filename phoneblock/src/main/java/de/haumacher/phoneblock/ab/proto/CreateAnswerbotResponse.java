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

	/** @see #getUserName() */
	public static final String USER_NAME__PROP = "userName";

	private String _userName = "";

	/**
	 * Creates a {@link CreateAnswerbotResponse} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse#create()
	 */
	protected CreateAnswerbotResponse() {
		super();
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

	@Override
	public String jsonType() {
		return CREATE_ANSWERBOT_RESPONSE__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			USER_NAME__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case USER_NAME__PROP: return getUserName();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case USER_NAME__PROP: internalSetUserName((String) value); break;
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
		out.name(USER_NAME__PROP);
		out.value(getUserName());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case USER_NAME__PROP: setUserName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

}
