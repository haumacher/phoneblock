package de.haumacher.phoneblock.ab.proto;

/**
 * Base class for all supported requests.
 */
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

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.UpdateAnswerBot}. */
		UPDATE_ANSWER_BOT,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.EnableAnswerBot}. */
		ENABLE_ANSWER_BOT,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.DisableAnswerBot}. */
		DISABLE_ANSWER_BOT,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.DeleteAnswerBot}. */
		DELETE_ANSWER_BOT,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.CheckAnswerBot}. */
		CHECK_ANSWER_BOT,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.ListCalls}. */
		LIST_CALLS,

		/** Type literal for {@link de.haumacher.phoneblock.ab.proto.ClearCallList}. */
		CLEAR_CALL_LIST,
		;

	}

	/** Visitor interface for the {@link de.haumacher.phoneblock.ab.proto.SetupRequest} hierarchy.*/
	public interface Visitor<R,A,E extends Throwable> extends de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> {

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.CreateAnswerBot}.*/
		R visit(de.haumacher.phoneblock.ab.proto.CreateAnswerBot self, A arg) throws E;

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
			case UpdateAnswerBot.UPDATE_ANSWER_BOT__TYPE: result = de.haumacher.phoneblock.ab.proto.UpdateAnswerBot.readUpdateAnswerBot(in); break;
			case EnableAnswerBot.ENABLE_ANSWER_BOT__TYPE: result = de.haumacher.phoneblock.ab.proto.EnableAnswerBot.readEnableAnswerBot(in); break;
			case DisableAnswerBot.DISABLE_ANSWER_BOT__TYPE: result = de.haumacher.phoneblock.ab.proto.DisableAnswerBot.readDisableAnswerBot(in); break;
			case DeleteAnswerBot.DELETE_ANSWER_BOT__TYPE: result = de.haumacher.phoneblock.ab.proto.DeleteAnswerBot.readDeleteAnswerBot(in); break;
			case CheckAnswerBot.CHECK_ANSWER_BOT__TYPE: result = de.haumacher.phoneblock.ab.proto.CheckAnswerBot.readCheckAnswerBot(in); break;
			case ListCalls.LIST_CALLS__TYPE: result = de.haumacher.phoneblock.ab.proto.ListCalls.readListCalls(in); break;
			case ClearCallList.CLEAR_CALL_LIST__TYPE: result = de.haumacher.phoneblock.ab.proto.ClearCallList.readClearCallList(in); break;
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
