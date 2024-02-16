package de.haumacher.phoneblock.ab.proto;

/**
 * Clears the calls answered so far.
 */
public class ClearCallList extends BotRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.ClearCallList} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.ClearCallList create() {
		return new de.haumacher.phoneblock.ab.proto.ClearCallList();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.ClearCallList} type in JSON format. */
	public static final String CLEAR_CALL_LIST__TYPE = "ClearCallList";

	/**
	 * Creates a {@link ClearCallList} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.ClearCallList#create()
	 */
	protected ClearCallList() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.CLEAR_CALL_LIST;
	}

	@Override
	public de.haumacher.phoneblock.ab.proto.ClearCallList setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public String jsonType() {
		return CLEAR_CALL_LIST__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.ClearCallList readClearCallList(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.ClearCallList result = new de.haumacher.phoneblock.ab.proto.ClearCallList();
		result.readContent(in);
		return result;
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
