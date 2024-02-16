package de.haumacher.phoneblock.ab.proto;

/**
 * Information about a SPAM call answered.
 */
public class CallInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.CallInfo} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.CallInfo create() {
		return new de.haumacher.phoneblock.ab.proto.CallInfo();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.CallInfo} type in JSON format. */
	public static final String CALL_INFO__TYPE = "CallInfo";

	/** @see #getCaller() */
	public static final String CALLER__PROP = "caller";

	/** @see #getStarted() */
	public static final String STARTED__PROP = "started";

	/** @see #getDuration() */
	public static final String DURATION__PROP = "duration";

	private String _caller = "";

	private long _started = 0L;

	private long _duration = 0L;

	/**
	 * Creates a {@link CallInfo} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.CallInfo#create()
	 */
	protected CallInfo() {
		super();
	}

	/**
	 * The phone number of the caller.
	 */
	public final String getCaller() {
		return _caller;
	}

	/**
	 * @see #getCaller()
	 */
	public de.haumacher.phoneblock.ab.proto.CallInfo setCaller(String value) {
		internalSetCaller(value);
		return this;
	}

	/** Internal setter for {@link #getCaller()} without chain call utility. */
	protected final void internalSetCaller(String value) {
		_caller = value;
	}

	/**
	 * The time the call has started.
	 */
	public final long getStarted() {
		return _started;
	}

	/**
	 * @see #getStarted()
	 */
	public de.haumacher.phoneblock.ab.proto.CallInfo setStarted(long value) {
		internalSetStarted(value);
		return this;
	}

	/** Internal setter for {@link #getStarted()} without chain call utility. */
	protected final void internalSetStarted(long value) {
		_started = value;
	}

	/**
	 * The duration of the call in milliseconds.
	 */
	public final long getDuration() {
		return _duration;
	}

	/**
	 * @see #getDuration()
	 */
	public de.haumacher.phoneblock.ab.proto.CallInfo setDuration(long value) {
		internalSetDuration(value);
		return this;
	}

	/** Internal setter for {@link #getDuration()} without chain call utility. */
	protected final void internalSetDuration(long value) {
		_duration = value;
	}

	@Override
	public String jsonType() {
		return CALL_INFO__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			CALLER__PROP, 
			STARTED__PROP, 
			DURATION__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case CALLER__PROP: return getCaller();
			case STARTED__PROP: return getStarted();
			case DURATION__PROP: return getDuration();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case CALLER__PROP: internalSetCaller((String) value); break;
			case STARTED__PROP: internalSetStarted((long) value); break;
			case DURATION__PROP: internalSetDuration((long) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.CallInfo readCallInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.CallInfo result = new de.haumacher.phoneblock.ab.proto.CallInfo();
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
		out.name(CALLER__PROP);
		out.value(getCaller());
		out.name(STARTED__PROP);
		out.value(getStarted());
		out.name(DURATION__PROP);
		out.value(getDuration());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case CALLER__PROP: setCaller(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case STARTED__PROP: setStarted(in.nextLong()); break;
			case DURATION__PROP: setDuration(in.nextLong()); break;
			default: super.readField(in, field);
		}
	}

}
