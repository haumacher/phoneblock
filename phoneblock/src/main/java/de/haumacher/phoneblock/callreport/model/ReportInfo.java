package de.haumacher.phoneblock.callreport.model;

/**
 * Message sent by the CallReportServlet upon GET request.
 */
public class ReportInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.callreport.model.ReportInfo} instance.
	 */
	public static de.haumacher.phoneblock.callreport.model.ReportInfo create() {
		return new de.haumacher.phoneblock.callreport.model.ReportInfo();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.callreport.model.ReportInfo} type in JSON format. */
	public static final String REPORT_INFO__TYPE = "ReportInfo";

	/** @see #getTimestamp() */
	public static final String TIMESTAMP__PROP = "timestamp";

	/** @see #getLastid() */
	public static final String LASTID__PROP = "lastid";

	private String _timestamp = null;

	private String _lastid = null;

	/**
	 * Creates a {@link ReportInfo} instance.
	 *
	 * @see de.haumacher.phoneblock.callreport.model.ReportInfo#create()
	 */
	protected ReportInfo() {
		super();
	}

	/**
	 * The {@link CallReport#getTimestamp()} value that was received during the last update.
	 */
	public final String getTimestamp() {
		return _timestamp;
	}

	/**
	 * @see #getTimestamp()
	 */
	public de.haumacher.phoneblock.callreport.model.ReportInfo setTimestamp(String value) {
		internalSetTimestamp(value);
		return this;
	}

	/** Internal setter for {@link #getTimestamp()} without chain call utility. */
	protected final void internalSetTimestamp(String value) {
		_timestamp = value;
	}

	/**
	 * Checks, whether {@link #getTimestamp()} has a value.
	 */
	public final boolean hasTimestamp() {
		return _timestamp != null;
	}

	/**
	 * The {@link CallReport#getLastid()} value that was received during the last update.
	 */
	public final String getLastid() {
		return _lastid;
	}

	/**
	 * @see #getLastid()
	 */
	public de.haumacher.phoneblock.callreport.model.ReportInfo setLastid(String value) {
		internalSetLastid(value);
		return this;
	}

	/** Internal setter for {@link #getLastid()} without chain call utility. */
	protected final void internalSetLastid(String value) {
		_lastid = value;
	}

	/**
	 * Checks, whether {@link #getLastid()} has a value.
	 */
	public final boolean hasLastid() {
		return _lastid != null;
	}

	@Override
	public String jsonType() {
		return REPORT_INFO__TYPE;
	}

	static final java.util.List<String> PROPERTIES;
	static {
		java.util.List<String> local = java.util.Arrays.asList(
			TIMESTAMP__PROP, 
			LASTID__PROP);
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
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case TIMESTAMP__PROP: internalSetTimestamp((String) value); break;
			case LASTID__PROP: internalSetLastid((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.callreport.model.ReportInfo readReportInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.callreport.model.ReportInfo result = new de.haumacher.phoneblock.callreport.model.ReportInfo();
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
		if (hasTimestamp()) {
			out.name(TIMESTAMP__PROP);
			out.value(getTimestamp());
		}
		if (hasLastid()) {
			out.name(LASTID__PROP);
			out.value(getLastid());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case TIMESTAMP__PROP: setTimestamp(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LASTID__PROP: setLastid(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

}
