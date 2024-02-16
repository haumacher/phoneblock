package de.haumacher.phoneblock.ab.proto;

/**
 * Answer to a {@link ListCalls} request.
 */
public class ListCallsResponse extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.ListCallsResponse} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.ListCallsResponse create() {
		return new de.haumacher.phoneblock.ab.proto.ListCallsResponse();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.ListCallsResponse} type in JSON format. */
	public static final String LIST_CALLS_RESPONSE__TYPE = "ListCallsResponse";

	/** @see #getCallsAnswered() */
	public static final String CALLS_ANSWERED__PROP = "callsAnswered";

	/** @see #getTalkTime() */
	public static final String TALK_TIME__PROP = "talkTime";

	/** @see #getCalls() */
	public static final String CALLS__PROP = "calls";

	private int _callsAnswered = 0;

	private long _talkTime = 0L;

	private final java.util.List<de.haumacher.phoneblock.ab.proto.CallInfo> _calls = new java.util.ArrayList<>();

	/**
	 * Creates a {@link ListCallsResponse} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.ListCallsResponse#create()
	 */
	protected ListCallsResponse() {
		super();
	}

	/**
	 * The total number of calls answered so far.
	 */
	public final int getCallsAnswered() {
		return _callsAnswered;
	}

	/**
	 * @see #getCallsAnswered()
	 */
	public de.haumacher.phoneblock.ab.proto.ListCallsResponse setCallsAnswered(int value) {
		internalSetCallsAnswered(value);
		return this;
	}

	/** Internal setter for {@link #getCallsAnswered()} without chain call utility. */
	protected final void internalSetCallsAnswered(int value) {
		_callsAnswered = value;
	}

	/**
	 * The total amout of time taked to SPAM callers so far.
	 */
	public final long getTalkTime() {
		return _talkTime;
	}

	/**
	 * @see #getTalkTime()
	 */
	public de.haumacher.phoneblock.ab.proto.ListCallsResponse setTalkTime(long value) {
		internalSetTalkTime(value);
		return this;
	}

	/** Internal setter for {@link #getTalkTime()} without chain call utility. */
	protected final void internalSetTalkTime(long value) {
		_talkTime = value;
	}

	/**
	 * The last calls that have been answered.
	 */
	public final java.util.List<de.haumacher.phoneblock.ab.proto.CallInfo> getCalls() {
		return _calls;
	}

	/**
	 * @see #getCalls()
	 */
	public de.haumacher.phoneblock.ab.proto.ListCallsResponse setCalls(java.util.List<? extends de.haumacher.phoneblock.ab.proto.CallInfo> value) {
		internalSetCalls(value);
		return this;
	}

	/** Internal setter for {@link #getCalls()} without chain call utility. */
	protected final void internalSetCalls(java.util.List<? extends de.haumacher.phoneblock.ab.proto.CallInfo> value) {
		if (value == null) throw new IllegalArgumentException("Property 'calls' cannot be null.");
		_calls.clear();
		_calls.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getCalls()} list.
	 */
	public de.haumacher.phoneblock.ab.proto.ListCallsResponse addCall(de.haumacher.phoneblock.ab.proto.CallInfo value) {
		internalAddCall(value);
		return this;
	}

	/** Implementation of {@link #addCall(de.haumacher.phoneblock.ab.proto.CallInfo)} without chain call utility. */
	protected final void internalAddCall(de.haumacher.phoneblock.ab.proto.CallInfo value) {
		_calls.add(value);
	}

	/**
	 * Removes a value from the {@link #getCalls()} list.
	 */
	public final void removeCall(de.haumacher.phoneblock.ab.proto.CallInfo value) {
		_calls.remove(value);
	}

	@Override
	public String jsonType() {
		return LIST_CALLS_RESPONSE__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			CALLS_ANSWERED__PROP, 
			TALK_TIME__PROP, 
			CALLS__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case CALLS_ANSWERED__PROP: return getCallsAnswered();
			case TALK_TIME__PROP: return getTalkTime();
			case CALLS__PROP: return getCalls();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case CALLS_ANSWERED__PROP: internalSetCallsAnswered((int) value); break;
			case TALK_TIME__PROP: internalSetTalkTime((long) value); break;
			case CALLS__PROP: internalSetCalls(de.haumacher.msgbuf.util.Conversions.asList(de.haumacher.phoneblock.ab.proto.CallInfo.class, value)); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.ListCallsResponse readListCallsResponse(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.ListCallsResponse result = new de.haumacher.phoneblock.ab.proto.ListCallsResponse();
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
		out.name(CALLS_ANSWERED__PROP);
		out.value(getCallsAnswered());
		out.name(TALK_TIME__PROP);
		out.value(getTalkTime());
		out.name(CALLS__PROP);
		out.beginArray();
		for (de.haumacher.phoneblock.ab.proto.CallInfo x : getCalls()) {
			x.writeTo(out);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case CALLS_ANSWERED__PROP: setCallsAnswered(in.nextInt()); break;
			case TALK_TIME__PROP: setTalkTime(in.nextLong()); break;
			case CALLS__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addCall(de.haumacher.phoneblock.ab.proto.CallInfo.readCallInfo(in));
				}
				in.endArray();
			}
			break;
			default: super.readField(in, field);
		}
	}

}
