package de.haumacher.phoneblock.ab.proto;

/**
 * Checks whether an answer bot has successfully registered to its Fritz!Box.
 */
public class CheckAnswerBot extends BotRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.CheckAnswerBot} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.CheckAnswerBot create() {
		return new de.haumacher.phoneblock.ab.proto.CheckAnswerBot();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.CheckAnswerBot} type in JSON format. */
	public static final String CHECK_ANSWER_BOT__TYPE = "CheckAnswerBot";

	/**
	 * Creates a {@link CheckAnswerBot} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.CheckAnswerBot#create()
	 */
	protected CheckAnswerBot() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.CHECK_ANSWER_BOT;
	}

	@Override
	public de.haumacher.phoneblock.ab.proto.CheckAnswerBot setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public String jsonType() {
		return CHECK_ANSWER_BOT__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.CheckAnswerBot readCheckAnswerBot(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.CheckAnswerBot result = new de.haumacher.phoneblock.ab.proto.CheckAnswerBot();
		result.readContent(in);
		return result;
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
