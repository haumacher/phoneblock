package de.haumacher.phoneblock.ab.proto;

public class CreateAnswerBot extends SetupRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.CreateAnswerBot} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.CreateAnswerBot create() {
		return new de.haumacher.phoneblock.ab.proto.CreateAnswerBot();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.CreateAnswerBot} type in JSON format. */
	public static final String CREATE_ANSWER_BOT__TYPE = "CreateAnswerBot";

	/**
	 * Creates a {@link CreateAnswerBot} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.CreateAnswerBot#create()
	 */
	protected CreateAnswerBot() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.CREATE_ANSWER_BOT;
	}

	@Override
	public String jsonType() {
		return CREATE_ANSWER_BOT__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.CreateAnswerBot readCreateAnswerBot(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.CreateAnswerBot result = new de.haumacher.phoneblock.ab.proto.CreateAnswerBot();
		result.readContent(in);
		return result;
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.SetupRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
