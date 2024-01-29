package de.haumacher.phoneblock.ab.proto;

/**
 * Switches the answer bot on.
 */
public class EnableAnswerBot extends BotRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.EnableAnswerBot} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.EnableAnswerBot create() {
		return new de.haumacher.phoneblock.ab.proto.EnableAnswerBot();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.EnableAnswerBot} type in JSON format. */
	public static final String ENABLE_ANSWER_BOT__TYPE = "EnableAnswerBot";

	/**
	 * Creates a {@link EnableAnswerBot} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.EnableAnswerBot#create()
	 */
	protected EnableAnswerBot() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.ENABLE_ANSWER_BOT;
	}

	@Override
	public de.haumacher.phoneblock.ab.proto.EnableAnswerBot setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public String jsonType() {
		return ENABLE_ANSWER_BOT__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.EnableAnswerBot readEnableAnswerBot(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.EnableAnswerBot result = new de.haumacher.phoneblock.ab.proto.EnableAnswerBot();
		result.readContent(in);
		return result;
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
