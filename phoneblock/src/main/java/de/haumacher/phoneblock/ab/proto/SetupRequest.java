package de.haumacher.phoneblock.ab.proto;

public abstract class SetupRequest extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/** Type codes for the {@link de.haumacher.phoneblock.ab.proto.SetupRequest} hierarchy. */
	public enum TypeKind {

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.CreateAnswerBot}. */
		CREATE_ANSWER_BOT,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.EnterHostName}. */
		ENTER_HOST_NAME,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.SetupDynDns}. */
		SETUP_DYN_DNS,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.CheckDynDns}. */
		CHECK_DYN_DNS,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.EnableAnswerBot}. */
		ENABLE_ANSWER_BOT,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.DisableAnswerBot}. */
		DISABLE_ANSWER_BOT,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.CheckAnswerBot}. */
		CHECK_ANSWER_BOT,
		;

	}

	/** Visitor interface for the {@link de.haumacher.phoneblock.ab.proto.SetupRequest} hierarchy.*/
	public interface Visitor<R,A,E extends Throwable> {

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.CreateAnswerBot}.*/
		R visit(de.haumacher.phoneblock.ab.proto.CreateAnswerBot self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.EnterHostName}.*/
		R visit(de.haumacher.phoneblock.ab.proto.EnterHostName self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.SetupDynDns}.*/
		R visit(de.haumacher.phoneblock.ab.proto.SetupDynDns self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.CheckDynDns}.*/
		R visit(de.haumacher.phoneblock.ab.proto.CheckDynDns self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.EnableAnswerBot}.*/
		R visit(de.haumacher.phoneblock.ab.proto.EnableAnswerBot self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.DisableAnswerBot}.*/
		R visit(de.haumacher.phoneblock.ab.proto.DisableAnswerBot self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.CheckAnswerBot}.*/
		R visit(de.haumacher.phoneblock.ab.proto.CheckAnswerBot self, A arg) throws E;

	}

	/**
	 * Creates a {@link SetupRequest} instance.
	 */
	protected SetupRequest() {
		super();
	}

	/** The type code of this instance. */
	public abstract TypeKind kind();

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.SetupRequest readSetupRequest(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.SetupRequest result;
		in.beginArray();
		String type = in.nextString();
		switch (type) {
			case CreateAnswerBot.CREATE_ANSWER_BOT__TYPE: result = de.haumacher.phoneblock.ab.proto.CreateAnswerBot.readCreateAnswerBot(in); break;
			case EnterHostName.ENTER_HOST_NAME__TYPE: result = de.haumacher.phoneblock.ab.proto.EnterHostName.readEnterHostName(in); break;
			case SetupDynDns.SETUP_DYN_DNS__TYPE: result = de.haumacher.phoneblock.ab.proto.SetupDynDns.readSetupDynDns(in); break;
			case CheckDynDns.CHECK_DYN_DNS__TYPE: result = de.haumacher.phoneblock.ab.proto.CheckDynDns.readCheckDynDns(in); break;
			case EnableAnswerBot.ENABLE_ANSWER_BOT__TYPE: result = de.haumacher.phoneblock.ab.proto.EnableAnswerBot.readEnableAnswerBot(in); break;
			case DisableAnswerBot.DISABLE_ANSWER_BOT__TYPE: result = de.haumacher.phoneblock.ab.proto.DisableAnswerBot.readDisableAnswerBot(in); break;
			case CheckAnswerBot.CHECK_ANSWER_BOT__TYPE: result = de.haumacher.phoneblock.ab.proto.CheckAnswerBot.readCheckAnswerBot(in); break;
			default: in.skipValue(); result = null; break;
		}
		in.endArray();
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		out.beginArray();
		out.value(jsonType());
		writeContent(out);
		out.endArray();
	}

	/** Accepts the given visitor. */
	public abstract <R,A,E extends Throwable> R visit(Visitor<R,A,E> v, A arg) throws E;

}
