package de.haumacher.phoneblock.ab.proto;

/**
 * Result of the {@link de.haumacher.phoneblock.ab.ListABServlet}.
 */
public class ListAnswerbotResponse extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse create() {
		return new de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse} type in JSON format. */
	public static final String LIST_ANSWERBOT_RESPONSE__TYPE = "ListAnswerbotResponse";

	/** @see #getBots() */
	public static final String BOTS__PROP = "bots";

	private final java.util.List<de.haumacher.phoneblock.ab.proto.AnswerbotInfo> _bots = new java.util.ArrayList<>();

	/**
	 * Creates a {@link ListAnswerbotResponse} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse#create()
	 */
	protected ListAnswerbotResponse() {
		super();
	}

	/**
	 * Infos for all answer bots of the current user.
	 */
	public final java.util.List<de.haumacher.phoneblock.ab.proto.AnswerbotInfo> getBots() {
		return _bots;
	}

	/**
	 * @see #getBots()
	 */
	public de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse setBots(java.util.List<? extends de.haumacher.phoneblock.ab.proto.AnswerbotInfo> value) {
		internalSetBots(value);
		return this;
	}

	/** Internal setter for {@link #getBots()} without chain call utility. */
	protected final void internalSetBots(java.util.List<? extends de.haumacher.phoneblock.ab.proto.AnswerbotInfo> value) {
		if (value == null) throw new IllegalArgumentException("Property 'bots' cannot be null.");
		_bots.clear();
		_bots.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getBots()} list.
	 */
	public de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse addBot(de.haumacher.phoneblock.ab.proto.AnswerbotInfo value) {
		internalAddBot(value);
		return this;
	}

	/** Implementation of {@link #addBot(de.haumacher.phoneblock.ab.proto.AnswerbotInfo)} without chain call utility. */
	protected final void internalAddBot(de.haumacher.phoneblock.ab.proto.AnswerbotInfo value) {
		_bots.add(value);
	}

	/**
	 * Removes a value from the {@link #getBots()} list.
	 */
	public final void removeBot(de.haumacher.phoneblock.ab.proto.AnswerbotInfo value) {
		_bots.remove(value);
	}

	@Override
	public String jsonType() {
		return LIST_ANSWERBOT_RESPONSE__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			BOTS__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case BOTS__PROP: return getBots();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case BOTS__PROP: internalSetBots(de.haumacher.msgbuf.util.Conversions.asList(de.haumacher.phoneblock.ab.proto.AnswerbotInfo.class, value)); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse readListAnswerbotResponse(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse result = new de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse();
		result.readContent(in);
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		writeContent(out);
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(BOTS__PROP);
		out.beginArray();
		for (de.haumacher.phoneblock.ab.proto.AnswerbotInfo x : getBots()) {
			x.writeTo(out);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case BOTS__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addBot(de.haumacher.phoneblock.ab.proto.AnswerbotInfo.readAnswerbotInfo(in));
				}
				in.endArray();
			}
			break;
			default: super.readField(in, field);
		}
	}

}
