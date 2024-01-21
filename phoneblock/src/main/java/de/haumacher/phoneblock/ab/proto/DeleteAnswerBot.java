package de.haumacher.phoneblock.ab.proto;

public class DeleteAnswerBot extends SetupRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.DeleteAnswerBot} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.DeleteAnswerBot create() {
		return new de.haumacher.phoneblock.ab.proto.DeleteAnswerBot();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.DeleteAnswerBot} type in JSON format. */
	public static final String DELETE_ANSWER_BOT__TYPE = "DeleteAnswerBot";

	/** @see #getId() */
	public static final String ID__PROP = "id";

	private long _id = 0L;

	/**
	 * Creates a {@link DeleteAnswerBot} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.DeleteAnswerBot#create()
	 */
	protected DeleteAnswerBot() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.DELETE_ANSWER_BOT;
	}

	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.ab.proto.DeleteAnswerBot setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_id = value;
	}

	@Override
	public String jsonType() {
		return DELETE_ANSWER_BOT__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ID__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ID__PROP: return getId();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ID__PROP: internalSetId((long) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.DeleteAnswerBot readDeleteAnswerBot(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.DeleteAnswerBot result = new de.haumacher.phoneblock.ab.proto.DeleteAnswerBot();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(ID__PROP);
		out.value(getId());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(in.nextLong()); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.SetupRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
