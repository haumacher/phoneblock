package de.haumacher.phoneblock.ab.proto;

/**
 * Retrieves a list of calls this answer bot has already answered.
 */
public class ListCalls extends BotRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.ListCalls} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.ListCalls create() {
		return new de.haumacher.phoneblock.ab.proto.ListCalls();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.ListCalls} type in JSON format. */
	public static final String LIST_CALLS__TYPE = "ListCalls";

	/**
	 * Creates a {@link ListCalls} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.ListCalls#create()
	 */
	protected ListCalls() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.LIST_CALLS;
	}

	@Override
	public de.haumacher.phoneblock.ab.proto.ListCalls setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public String jsonType() {
		return LIST_CALLS__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.ListCalls readListCalls(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.ListCalls result = new de.haumacher.phoneblock.ab.proto.ListCalls();
		result.readContent(in);
		return result;
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
