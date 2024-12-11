package de.haumacher.phoneblock.ab.proto;

/**
 * Base class for all requests targeting a single answer bot.
 */
public abstract class BotRequest extends SetupRequest {

	/** Visitor interface for the {@link de.haumacher.phoneblock.ab.proto.BotRequest} hierarchy.*/
	public interface Visitor<R,A,E extends Throwable> {

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.EnterHostName}.*/
		R visit(de.haumacher.phoneblock.ab.proto.EnterHostName self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.SetupDynDns}.*/
		R visit(de.haumacher.phoneblock.ab.proto.SetupDynDns self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.CheckDynDns}.*/
		R visit(de.haumacher.phoneblock.ab.proto.CheckDynDns self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.UpdateAnswerBot}.*/
		R visit(de.haumacher.phoneblock.ab.proto.UpdateAnswerBot self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.EnableAnswerBot}.*/
		R visit(de.haumacher.phoneblock.ab.proto.EnableAnswerBot self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.DisableAnswerBot}.*/
		R visit(de.haumacher.phoneblock.ab.proto.DisableAnswerBot self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.DeleteAnswerBot}.*/
		R visit(de.haumacher.phoneblock.ab.proto.DeleteAnswerBot self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.CheckAnswerBot}.*/
		R visit(de.haumacher.phoneblock.ab.proto.CheckAnswerBot self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.ListCalls}.*/
		R visit(de.haumacher.phoneblock.ab.proto.ListCalls self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.ab.proto.ClearCallList}.*/
		R visit(de.haumacher.phoneblock.ab.proto.ClearCallList self, A arg) throws E;

	}

	/** @see #getId() */
	public static final String ID__PROP = "id";

	private long _id = 0L;

	/**
	 * Creates a {@link BotRequest} instance.
	 */
	protected BotRequest() {
		super();
	}

	/**
	 * The ID of the answer bot this request is targeted to
	 */
	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.ab.proto.BotRequest setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_id = value;
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
	public static de.haumacher.phoneblock.ab.proto.BotRequest readBotRequest(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.BotRequest result;
		in.beginArray();
		String type = in.nextString();
		switch (type) {
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

	/** Accepts the given visitor. */
	public abstract <R,A,E extends Throwable> R visit(Visitor<R,A,E> v, A arg) throws E;

	@Override
	public final <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.SetupRequest.Visitor<R,A,E> v, A arg) throws E {
		return visit((de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E>) v, arg);
	}

}
