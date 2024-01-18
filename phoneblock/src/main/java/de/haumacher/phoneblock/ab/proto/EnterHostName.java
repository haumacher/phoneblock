package de.haumacher.phoneblock.ab.proto;

public class EnterHostName extends SetupRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.EnterHostName} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.EnterHostName create() {
		return new de.haumacher.phoneblock.ab.proto.EnterHostName();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.EnterHostName} type in JSON format. */
	public static final String ENTER_HOST_NAME__TYPE = "EnterHostName";

	/** @see #getId() */
	public static final String ID__PROP = "id";

	/** @see #getHostName() */
	public static final String HOST_NAME__PROP = "hostName";

	private long _id = 0L;

	private String _hostName = "";

	/**
	 * Creates a {@link EnterHostName} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.EnterHostName#create()
	 */
	protected EnterHostName() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.ENTER_HOST_NAME;
	}

	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.ab.proto.EnterHostName setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_id = value;
	}

	public final String getHostName() {
		return _hostName;
	}

	/**
	 * @see #getHostName()
	 */
	public de.haumacher.phoneblock.ab.proto.EnterHostName setHostName(String value) {
		internalSetHostName(value);
		return this;
	}

	/** Internal setter for {@link #getHostName()} without chain call utility. */
	protected final void internalSetHostName(String value) {
		_hostName = value;
	}

	@Override
	public String jsonType() {
		return ENTER_HOST_NAME__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ID__PROP, 
			HOST_NAME__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ID__PROP: return getId();
			case HOST_NAME__PROP: return getHostName();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ID__PROP: internalSetId((long) value); break;
			case HOST_NAME__PROP: internalSetHostName((String) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.EnterHostName readEnterHostName(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.EnterHostName result = new de.haumacher.phoneblock.ab.proto.EnterHostName();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(ID__PROP);
		out.value(getId());
		out.name(HOST_NAME__PROP);
		out.value(getHostName());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(in.nextLong()); break;
			case HOST_NAME__PROP: setHostName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.SetupRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
