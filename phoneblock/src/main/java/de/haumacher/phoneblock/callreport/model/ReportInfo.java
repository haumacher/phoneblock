package de.haumacher.phoneblock.callreport.model;

/**
 * Message sent by the CallReportServlet upon GET request.
 */
public class ReportInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link ReportInfo} instance.
	 */
	public static ReportInfo create() {
		return new ReportInfo();
	}

	/** Identifier for the {@link ReportInfo} type in JSON format. */
	public static final String REPORT_INFO__TYPE = "ReportInfo";

	/** @see #getTimestamp() */
	public static final String TIMESTAMP = "timestamp";

	/** @see #getLastid() */
	public static final String LASTID = "lastid";

	/** Identifier for the property {@link #getTimestamp()} in binary format. */
	public static final int TIMESTAMP__ID = 1;

	/** Identifier for the property {@link #getLastid()} in binary format. */
	public static final int LASTID__ID = 2;

	private String _timestamp = null;

	private String _lastid = null;

	/**
	 * Creates a {@link ReportInfo} instance.
	 *
	 * @see #create()
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
	public ReportInfo setTimestamp(String value) {
		internalSetTimestamp(value);
		return this;
	}
	/** Internal setter for {@link #getTimestamp()} without chain call utility. */
	protected final void internalSetTimestamp(String value) {
		_listener.beforeSet(this, TIMESTAMP, value);
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
	public ReportInfo setLastid(String value) {
		internalSetLastid(value);
		return this;
	}
	/** Internal setter for {@link #getLastid()} without chain call utility. */
	protected final void internalSetLastid(String value) {
		_listener.beforeSet(this, LASTID, value);
		_lastid = value;
	}


	/**
	 * Checks, whether {@link #getLastid()} has a value.
	 */
	public final boolean hasLastid() {
		return _lastid != null;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public ReportInfo registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public ReportInfo unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return REPORT_INFO__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			TIMESTAMP, 
			LASTID));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case TIMESTAMP: return getTimestamp();
			case LASTID: return getLastid();
			default: return de.haumacher.msgbuf.observer.Observable.super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case TIMESTAMP: setTimestamp((String) value); break;
			case LASTID: setLastid((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static ReportInfo readReportInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		ReportInfo result = new ReportInfo();
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
		if (hasTimestamp()) {
			out.name(TIMESTAMP);
			out.value(getTimestamp());
		}
		if (hasLastid()) {
			out.name(LASTID);
			out.value(getLastid());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case TIMESTAMP: setTimestamp(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LASTID: setLastid(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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
		if (hasTimestamp()) {
			out.name(TIMESTAMP__ID);
			out.value(getTimestamp());
		}
		if (hasLastid()) {
			out.name(LASTID__ID);
			out.value(getLastid());
		}
	}

	/** Reads a new instance from the given reader. */
	public static ReportInfo readReportInfo(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		ReportInfo result = new ReportInfo();
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
			default: in.skipValue(); 
		}
	}

}
