package de.haumacher.phoneblock.ab.proto;

/**
 * Deletes an answer bot.
 */
public class DeleteAnswerBot extends BotRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.DeleteAnswerBot} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.DeleteAnswerBot create() {
		return new de.haumacher.phoneblock.ab.proto.DeleteAnswerBot();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.DeleteAnswerBot} type in JSON format. */
	public static final String DELETE_ANSWER_BOT__TYPE = "DeleteAnswerBot";

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

	@Override
	public de.haumacher.phoneblock.ab.proto.DeleteAnswerBot setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public String jsonType() {
		return DELETE_ANSWER_BOT__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.DeleteAnswerBot readDeleteAnswerBot(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.DeleteAnswerBot result = new de.haumacher.phoneblock.ab.proto.DeleteAnswerBot();
		result.readContent(in);
		return result;
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
