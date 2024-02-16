package de.haumacher.phoneblock.db.settings;

/**
 * Common options of answer bot settings.
 */
public abstract class AnswerBotSetting extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

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
	public static final String ID__PROP = "id";

	/** @see #getUserId() */
	public static final String USER_ID__PROP = "userId";

	/** @see #getCreated() */
	public static final String CREATED__PROP = "created";

	/** @see #getUpdated() */
	public static final String UPDATED__PROP = "updated";

	/** Identifier for the property {@link #getId()} in binary format. */
	static final int ID__ID = 1;

	/** Identifier for the property {@link #getUserId()} in binary format. */
	static final int USER_ID__ID = 2;

	/** Identifier for the property {@link #getCreated()} in binary format. */
	static final int CREATED__ID = 3;

	/** Identifier for the property {@link #getUpdated()} in binary format. */
	static final int UPDATED__ID = 4;

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
		_listener.beforeSet(this, ID__PROP, value);
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
		_listener.beforeSet(this, USER_ID__PROP, value);
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
		_listener.beforeSet(this, CREATED__PROP, value);
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
		_listener.beforeSet(this, UPDATED__PROP, value);
		_updated = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.settings.AnswerBotSetting registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.settings.AnswerBotSetting unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ID__PROP, 
			USER_ID__PROP, 
			CREATED__PROP, 
			UPDATED__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ID__PROP: return getId();
			case USER_ID__PROP: return getUserId();
			case CREATED__PROP: return getCreated();
			case UPDATED__PROP: return getUpdated();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ID__PROP: internalSetId((long) value); break;
			case USER_ID__PROP: internalSetUserId((long) value); break;
			case CREATED__PROP: internalSetCreated((long) value); break;
			case UPDATED__PROP: internalSetUpdated((long) value); break;
		}
	}

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

	/** The binary identifier for this concrete type in the polymorphic {@link de.haumacher.phoneblock.db.settings.AnswerBotSetting} hierarchy. */
	abstract int typeId();

	@Override
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.beginObject();
		out.name(0);
		out.value(typeId());
		writeFields(out);
		out.endObject();
	}

	/**
	 * Serializes all fields of this instance to the given binary output.
	 *
	 * @param out
	 *        The binary output to write to.
	 * @throws java.io.IOException If writing fails.
	 */
	protected void writeFields(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.name(ID__ID);
		out.value(getId());
		out.name(USER_ID__ID);
		out.value(getUserId());
		out.name(CREATED__ID);
		out.value(getCreated());
		out.name(UPDATED__ID);
		out.value(getUpdated());
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.AnswerBotSetting readAnswerBotSetting(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		int typeField = in.nextName();
		assert typeField == 0;
		int type = in.nextInt();
		de.haumacher.phoneblock.db.settings.AnswerBotSetting result;
		switch (type) {
			case de.haumacher.phoneblock.db.settings.AnswerBotDynDns.ANSWER_BOT_DYN_DNS__TYPE_ID: result = de.haumacher.phoneblock.db.settings.AnswerBotDynDns.readAnswerBotDynDns_Content(in); break;
			case de.haumacher.phoneblock.db.settings.AnswerBotSip.ANSWER_BOT_SIP__TYPE_ID: result = de.haumacher.phoneblock.db.settings.AnswerBotSip.readAnswerBotSip_Content(in); break;
			default: result = null; while (in.hasNext()) {in.skipValue(); }
		}
		in.endObject();
		return result;
	}

	/** Helper for reading all fields of this instance. */
	protected final void readContent(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		while (in.hasNext()) {
			int field = in.nextName();
			readField(in, field);
		}
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case ID__ID: setId(in.nextLong()); break;
			case USER_ID__ID: setUserId(in.nextLong()); break;
			case CREATED__ID: setCreated(in.nextLong()); break;
			case UPDATED__ID: setUpdated(in.nextLong()); break;
			default: in.skipValue(); 
		}
	}

	/** Accepts the given visitor. */
	public abstract <R,A,E extends Throwable> R visit(Visitor<R,A,E> v, A arg) throws E;

}
