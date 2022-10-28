package de.haumacher.phoneblock.callreport.model;

/**
 * Message expected by the CallReportServlet in a PUT request.
 */
public class CallReport extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link CallReport} instance.
	 */
	public static CallReport create() {
		return new de.haumacher.phoneblock.callreport.model.CallReport();
	}

	/** Identifier for the {@link CallReport} type in JSON format. */
	public static final String CALL_REPORT__TYPE = "CallReport";

	/** @see #getTimestamp() */
	public static final String TIMESTAMP__PROP = "timestamp";

	/** @see #getLastid() */
	public static final String LASTID__PROP = "lastid";

	/** @see #getCallers() */
	public static final String CALLERS__PROP = "callers";

	/** Identifier for the property {@link #getTimestamp()} in binary format. */
	static final int TIMESTAMP__ID = 1;

	/** Identifier for the property {@link #getLastid()} in binary format. */
	static final int LASTID__ID = 2;

	/** Identifier for the property {@link #getCallers()} in binary format. */
	static final int CALLERS__ID = 3;

	private String _timestamp = "";

	private String _lastid = "";

	private final java.util.List<String> _callers = new de.haumacher.msgbuf.util.ReferenceList<String>() {
		@Override
		protected void beforeAdd(int index, String element) {
			_listener.beforeAdd(CallReport.this, CALLERS__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, String element) {
			_listener.afterRemove(CallReport.this, CALLERS__PROP, index, element);
		}
	};

	/**
	 * Creates a {@link CallReport} instance.
	 *
	 * @see CallReport#create()
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
		_listener.beforeSet(this, TIMESTAMP__PROP, value);
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
		_listener.beforeSet(this, LASTID__PROP, value);
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
	public CallReport setCallers(java.util.List<? extends String> value) {
		internalSetCallers(value);
		return this;
	}

	/** Internal setter for {@link #getCallers()} without chain call utility. */
	protected final void internalSetCallers(java.util.List<? extends String> value) {
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
			TIMESTAMP__PROP, 
			LASTID__PROP, 
			CALLERS__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case TIMESTAMP__PROP: return getTimestamp();
			case LASTID__PROP: return getLastid();
			case CALLERS__PROP: return getCallers();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case TIMESTAMP__PROP: internalSetTimestamp((String) value); break;
			case LASTID__PROP: internalSetLastid((String) value); break;
			case CALLERS__PROP: internalSetCallers(de.haumacher.msgbuf.util.Conversions.asList(String.class, value)); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static CallReport readCallReport(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.callreport.model.CallReport result = new de.haumacher.phoneblock.callreport.model.CallReport();
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
		out.name(TIMESTAMP__PROP);
		out.value(getTimestamp());
		out.name(LASTID__PROP);
		out.value(getLastid());
		out.name(CALLERS__PROP);
		out.beginArray();
		for (String x : getCallers()) {
			out.value(x);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case TIMESTAMP__PROP: setTimestamp(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LASTID__PROP: setLastid(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CALLERS__PROP: {
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
		CallReport result = de.haumacher.phoneblock.callreport.model.CallReport.readCallReport_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link CallReport} from a polymorphic composition. */
	public static CallReport readCallReport_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.callreport.model.CallReport result = new CallReport();
		result.readContent(in);
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
