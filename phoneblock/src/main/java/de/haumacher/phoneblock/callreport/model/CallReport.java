package de.haumacher.phoneblock.callreport.model;

/**
 * Message expected by the CallReportServlet in a PUT request.
 */
public class CallReport extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link CallReport} instance.
	 */
	public static CallReport create() {
		return new CallReport();
	}

	/** Identifier for the {@link CallReport} type in JSON format. */
	public static final String CALL_REPORT__TYPE = "CallReport";

	/** @see #getTimestamp() */
	public static final String TIMESTAMP = "timestamp";

	/** @see #getLastid() */
	public static final String LASTID = "lastid";

	/** @see #getCallers() */
	public static final String CALLERS = "callers";

	/** Identifier for the property {@link #getTimestamp()} in binary format. */
	public static final int TIMESTAMP__ID = 1;

	/** Identifier for the property {@link #getLastid()} in binary format. */
	public static final int LASTID__ID = 2;

	/** Identifier for the property {@link #getCallers()} in binary format. */
	public static final int CALLERS__ID = 3;

	private String _timestamp = "";

	private String _lastid = "";

	private final java.util.List<String> _callers = new de.haumacher.msgbuf.util.ReferenceList<String>() {
		@Override
		protected void beforeAdd(int index, String element) {
			_listener.beforeAdd(de.haumacher.phoneblock.callreport.model.CallReport.this, CALLERS, index, element);
		}

		@Override
		protected void afterRemove(int index, String element) {
			_listener.afterRemove(de.haumacher.phoneblock.callreport.model.CallReport.this, CALLERS, index, element);
		}
	};

	/**
	 * Creates a {@link CallReport} instance.
	 *
	 * @see #create()
	 */
	protected CallReport() {
		super();
	}

	/**
	 * The timestamp value as reported in a Fritz!Box GetCallers result.
	 */
	public final String getTimestamp() {
		return _timestamp;
	}

	/**
	 * @see #getTimestamp()
	 */
	public CallReport setTimestamp(String value) {
		internalSetTimestamp(value);
		return this;
	}
	/** Internal setter for {@link #getTimestamp()} without chain call utility. */
	protected final void internalSetTimestamp(String value) {
		_listener.beforeSet(this, TIMESTAMP, value);
		_timestamp = value;
	}


	/**
	 * The ID of the last call reported in a Fritz!Box GetCallers result.
	 */
	public final String getLastid() {
		return _lastid;
	}

	/**
	 * @see #getLastid()
	 */
	public CallReport setLastid(String value) {
		internalSetLastid(value);
		return this;
	}
	/** Internal setter for {@link #getLastid()} without chain call utility. */
	protected final void internalSetLastid(String value) {
		_listener.beforeSet(this, LASTID, value);
		_lastid = value;
	}


	/**
	 * The phone number that have been blocked be the Fritz!Box since the last report.
	 */
	public final java.util.List<String> getCallers() {
		return _callers;
	}

	/**
	 * @see #getCallers()
	 */
	public CallReport setCallers(java.util.List<String> value) {
		internalSetCallers(value);
		return this;
	}
	/** Internal setter for {@link #getCallers()} without chain call utility. */
	protected final void internalSetCallers(java.util.List<String> value) {
		_callers.clear();
		_callers.addAll(value);
	}


	/**
	 * Adds a value to the {@link #getCallers()} list.
	 */
	public CallReport addCaller(String value) {
		internalAddCaller(value);
		return this;
	}

	/** Implementation of {@link #addCaller(String)} without chain call utility. */
	protected final void internalAddCaller(String value) {
		_callers.add(value);
	}

	/**
	 * Removes a value from the {@link #getCallers()} list.
	 */
	public final void removeCaller(String value) {
		_callers.remove(value);
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public CallReport registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public CallReport unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return CALL_REPORT__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			TIMESTAMP, 
			LASTID, 
			CALLERS));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case TIMESTAMP: return getTimestamp();
			case LASTID: return getLastid();
			case CALLERS: return getCallers();
			default: return de.haumacher.msgbuf.observer.Observable.super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case TIMESTAMP: setTimestamp((String) value); break;
			case LASTID: setLastid((String) value); break;
			case CALLERS: setCallers((java.util.List<String>) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static CallReport readCallReport(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		CallReport result = new CallReport();
		in.beginObject();
		result.readFields(in);
		in.endObject();
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		writeContent(out);
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(TIMESTAMP);
		out.value(getTimestamp());
		out.name(LASTID);
		out.value(getLastid());
		out.name(CALLERS);
		out.beginArray();
		for (String x : getCallers()) {
			out.value(x);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case TIMESTAMP: setTimestamp(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LASTID: setLastid(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CALLERS: {
				in.beginArray();
				while (in.hasNext()) {
					addCaller(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
			}
			break;
			default: super.readField(in, field);
		}
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.beginObject();
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
		out.name(TIMESTAMP__ID);
		out.value(getTimestamp());
		out.name(LASTID__ID);
		out.value(getLastid());
		out.name(CALLERS__ID);
		{
			java.util.List<String> values = getCallers();
			out.beginArray(de.haumacher.msgbuf.binary.DataType.STRING, values.size());
			for (String x : values) {
				out.value(x);
			}
			out.endArray();
		}
	}

	/** Reads a new instance from the given reader. */
	public static CallReport readCallReport(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		CallReport result = new CallReport();
		while (in.hasNext()) {
			int field = in.nextName();
			result.readField(in, field);
		}
		in.endObject();
		return result;
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case TIMESTAMP__ID: setTimestamp(in.nextString()); break;
			case LASTID__ID: setLastid(in.nextString()); break;
			case CALLERS__ID: {
				in.beginArray();
				while (in.hasNext()) {
					addCaller(in.nextString());
				}
				in.endArray();
			}
			break;
			default: in.skipValue(); 
		}
	}

}
