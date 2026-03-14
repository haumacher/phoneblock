package de.haumacher.phoneblock.db.settings;

/**
 * Common options of answer bot settings.
 */
public abstract class AnswerBotSetting extends de.haumacher.msgbuf.data.AbstractDataObject {

	/** Type codes for the {@link de.haumacher.phoneblock.db.settings.AnswerBotSetting} hierarchy. */
	public enum TypeKind {

		/** Type literal for {@link de.haumacher.phoneblock.db.settings.AnswerBotDynDns}. */
		ANSWER_BOT_DYN_DNS,

		/** Type literal for {@link de.haumacher.phoneblock.db.settings.AnswerBotSip}. */
		ANSWER_BOT_SIP,
		;

	}

	/** Visitor interface for the {@link de.haumacher.phoneblock.db.settings.AnswerBotSetting} hierarchy.*/
	public interface Visitor<R,A,E extends Throwable> {

		/** Visit case for {@link de.haumacher.phoneblock.db.settings.AnswerBotDynDns}.*/
		R visit(de.haumacher.phoneblock.db.settings.AnswerBotDynDns self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.db.settings.AnswerBotSip}.*/
		R visit(de.haumacher.phoneblock.db.settings.AnswerBotSip self, A arg) throws E;

	}

	/** @see #getId() */
	private static final String ID__PROP = "id";

	/** @see #getUserId() */
	private static final String USER_ID__PROP = "userId";

	/** @see #getCreated() */
	private static final String CREATED__PROP = "created";

	/** @see #getUpdated() */
	private static final String UPDATED__PROP = "updated";

	private long _id = 0L;

	private long _userId = 0L;

	private long _created = 0L;

	private long _updated = 0L;

	/**
	 * Creates a {@link AnswerBotSetting} instance.
	 */
	protected AnswerBotSetting() {
		super();
	}

	/** The type code of this instance. */
	public abstract TypeKind kind();

	/**
	 * ID of the answer bot.
	 */
	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSetting setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_id = value;
	}

	/**
	 * ID of the PhoneBlock user.
	 */
	public final long getUserId() {
		return _userId;
	}

	/**
	 * @see #getUserId()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSetting setUserId(long value) {
		internalSetUserId(value);
		return this;
	}

	/** Internal setter for {@link #getUserId()} without chain call utility. */
	protected final void internalSetUserId(long value) {
		_userId = value;
	}

	/**
	 * Time when the setting was created.
	 */
	public final long getCreated() {
		return _created;
	}

	/**
	 * @see #getCreated()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSetting setCreated(long value) {
		internalSetCreated(value);
		return this;
	}

	/** Internal setter for {@link #getCreated()} without chain call utility. */
	protected final void internalSetCreated(long value) {
		_created = value;
	}

	/**
	 * Time when the setting was last updated.
	 */
	public final long getUpdated() {
		return _updated;
	}

	/**
	 * @see #getUpdated()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotSetting setUpdated(long value) {
		internalSetUpdated(value);
		return this;
	}

	/** Internal setter for {@link #getUpdated()} without chain call utility. */
	protected final void internalSetUpdated(long value) {
		_updated = value;
	}

	/** The type identifier for this concrete subtype. */
	public abstract String jsonType();

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.AnswerBotSetting readAnswerBotSetting(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.AnswerBotSetting result;
		in.beginArray();
		String type = in.nextString();
		switch (type) {
			case AnswerBotDynDns.ANSWER_BOT_DYN_DNS__TYPE: result = de.haumacher.phoneblock.db.settings.AnswerBotDynDns.readAnswerBotDynDns(in); break;
			case AnswerBotSip.ANSWER_BOT_SIP__TYPE: result = de.haumacher.phoneblock.db.settings.AnswerBotSip.readAnswerBotSip(in); break;
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

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(ID__PROP);
		out.value(getId());
		out.name(USER_ID__PROP);
		out.value(getUserId());
		out.name(CREATED__PROP);
		out.value(getCreated());
		out.name(UPDATED__PROP);
		out.value(getUpdated());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(in.nextLong()); break;
			case USER_ID__PROP: setUserId(in.nextLong()); break;
			case CREATED__PROP: setCreated(in.nextLong()); break;
			case UPDATED__PROP: setUpdated(in.nextLong()); break;
			default: super.readField(in, field);
		}
	}

	/** Accepts the given visitor. */
	public abstract <R,A,E extends Throwable> R visit(Visitor<R,A,E> v, A arg) throws E;

}
