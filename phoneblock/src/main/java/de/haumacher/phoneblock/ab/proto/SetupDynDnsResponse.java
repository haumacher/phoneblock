package de.haumacher.phoneblock.ab.proto;

public class SetupDynDnsResponse extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse create() {
		return new de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse} type in JSON format. */
	public static final String SETUP_DYN_DNS_RESPONSE__TYPE = "SetupDynDnsResponse";

	/** @see #getId() */
	public static final String ID__PROP = "id";

	/** @see #getDyndnsUser() */
	public static final String DYNDNS_USER__PROP = "dyndnsUser";

	/** @see #getDyndnsPassword() */
	public static final String DYNDNS_PASSWORD__PROP = "dyndnsPassword";

	/** @see #getDyndnsDomain() */
	public static final String DYNDNS_DOMAIN__PROP = "dyndnsDomain";

	private long _id = 0L;

	private String _dyndnsUser = "";

	private String _dyndnsPassword = "";

	private String _dyndnsDomain = "";

	/**
	 * Creates a {@link SetupDynDnsResponse} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse#create()
	 */
	protected SetupDynDnsResponse() {
		super();
	}

	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_id = value;
	}

	public final String getDyndnsUser() {
		return _dyndnsUser;
	}

	/**
	 * @see #getDyndnsUser()
	 */
	public de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse setDyndnsUser(String value) {
		internalSetDyndnsUser(value);
		return this;
	}

	/** Internal setter for {@link #getDyndnsUser()} without chain call utility. */
	protected final void internalSetDyndnsUser(String value) {
		_dyndnsUser = value;
	}

	public final String getDyndnsPassword() {
		return _dyndnsPassword;
	}

	/**
	 * @see #getDyndnsPassword()
	 */
	public de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse setDyndnsPassword(String value) {
		internalSetDyndnsPassword(value);
		return this;
	}

	/** Internal setter for {@link #getDyndnsPassword()} without chain call utility. */
	protected final void internalSetDyndnsPassword(String value) {
		_dyndnsPassword = value;
	}

	public final String getDyndnsDomain() {
		return _dyndnsDomain;
	}

	/**
	 * @see #getDyndnsDomain()
	 */
	public de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse setDyndnsDomain(String value) {
		internalSetDyndnsDomain(value);
		return this;
	}

	/** Internal setter for {@link #getDyndnsDomain()} without chain call utility. */
	protected final void internalSetDyndnsDomain(String value) {
		_dyndnsDomain = value;
	}

	@Override
	public String jsonType() {
		return SETUP_DYN_DNS_RESPONSE__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ID__PROP, 
			DYNDNS_USER__PROP, 
			DYNDNS_PASSWORD__PROP, 
			DYNDNS_DOMAIN__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ID__PROP: return getId();
			case DYNDNS_USER__PROP: return getDyndnsUser();
			case DYNDNS_PASSWORD__PROP: return getDyndnsPassword();
			case DYNDNS_DOMAIN__PROP: return getDyndnsDomain();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ID__PROP: internalSetId((long) value); break;
			case DYNDNS_USER__PROP: internalSetDyndnsUser((String) value); break;
			case DYNDNS_PASSWORD__PROP: internalSetDyndnsPassword((String) value); break;
			case DYNDNS_DOMAIN__PROP: internalSetDyndnsDomain((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse readSetupDynDnsResponse(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse result = new de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse();
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
		out.name(ID__PROP);
		out.value(getId());
		out.name(DYNDNS_USER__PROP);
		out.value(getDyndnsUser());
		out.name(DYNDNS_PASSWORD__PROP);
		out.value(getDyndnsPassword());
		out.name(DYNDNS_DOMAIN__PROP);
		out.value(getDyndnsDomain());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(in.nextLong()); break;
			case DYNDNS_USER__PROP: setDyndnsUser(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DYNDNS_PASSWORD__PROP: setDyndnsPassword(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DYNDNS_DOMAIN__PROP: setDyndnsDomain(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

}
