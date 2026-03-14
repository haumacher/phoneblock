package de.haumacher.phoneblock.callreport.model;

/**
 * Message expected by the CallReportServlet in a PUT request.
 */
public class CallReport extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.callreport.model.CallReport} instance.
	 */
	public static de.haumacher.phoneblock.callreport.model.CallReport create() {
		return new de.haumacher.phoneblock.callreport.model.CallReport();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.callreport.model.CallReport} type in JSON format. */
	public static final String CALL_REPORT__TYPE = "CallReport";

	/** @see #getTimestamp() */
	public static final String TIMESTAMP__PROP = "timestamp";

	/** @see #getLastid() */
	public static final String LASTID__PROP = "lastid";

	/** @see #getCallers() */
	public static final String CALLERS__PROP = "callers";

	private String _timestamp = "";

	private String _lastid = "";

	private final java.util.List<String> _callers = new java.util.ArrayList<>();

	/**
	 * Creates a {@link CallReport} instance.
	 *
	 * @see de.haumacher.phoneblock.callreport.model.CallReport#create()
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
	public de.haumacher.phoneblock.callreport.model.CallReport setTimestamp(String value) {
		internalSetTimestamp(value);
		return this;
	}

	/** Internal setter for {@link #getTimestamp()} without chain call utility. */
	protected final void internalSetTimestamp(String value) {
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
	public de.haumacher.phoneblock.callreport.model.CallReport setLastid(String value) {
		internalSetLastid(value);
		return this;
	}

	/** Internal setter for {@link #getLastid()} without chain call utility. */
	protected final void internalSetLastid(String value) {
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
	public de.haumacher.phoneblock.callreport.model.CallReport setCallers(java.util.List<? extends String> value) {
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
	public de.haumacher.phoneblock.callreport.model.CallReport addCaller(String value) {
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

	@Override
	public String jsonType() {
		return CALL_REPORT__TYPE;
	}

	static final java.util.List<String> PROPERTIES;
	static {
		java.util.List<String> local = java.util.Arrays.asList(
			TIMESTAMP__PROP, 
			LASTID__PROP, 
			CALLERS__PROP);
		PROPERTIES = java.util.Collections.unmodifiableList(local);
	}

	static final java.util.Set<String> TRANSIENT_PROPERTIES;
	static {
		java.util.HashSet<String> tmp = new java.util.HashSet<>();
		tmp.addAll(java.util.Arrays.asList(
				));
		TRANSIENT_PROPERTIES = java.util.Collections.unmodifiableSet(tmp);
	}

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public java.util.Set<String> transientProperties() {
		return TRANSIENT_PROPERTIES;
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
	public static de.haumacher.phoneblock.callreport.model.CallReport readCallReport(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
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
				java.util.List<String> newValue = new java.util.ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					newValue.add(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
				setCallers(newValue);
			}
			break;
			default: super.readField(in, field);
		}
	}

}
