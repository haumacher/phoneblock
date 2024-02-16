package de.haumacher.phoneblock.ab.proto;

/**
 * Switches the answer bot off.
 */
public class DisableAnswerBot extends BotRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.DisableAnswerBot} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.DisableAnswerBot create() {
		return new de.haumacher.phoneblock.ab.proto.DisableAnswerBot();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.DisableAnswerBot} type in JSON format. */
	public static final String DISABLE_ANSWER_BOT__TYPE = "DisableAnswerBot";

	/**
	 * Creates a {@link DisableAnswerBot} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.DisableAnswerBot#create()
	 */
	protected DisableAnswerBot() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.DISABLE_ANSWER_BOT;
	}

	@Override
	public de.haumacher.phoneblock.ab.proto.DisableAnswerBot setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public String jsonType() {
		return DISABLE_ANSWER_BOT__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.DisableAnswerBot readDisableAnswerBot(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.DisableAnswerBot result = new de.haumacher.phoneblock.ab.proto.DisableAnswerBot();
		result.readContent(in);
		return result;
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
