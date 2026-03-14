package de.haumacher.phoneblock.ab.proto;

/**
 * Sets up a DnyDNS account for the Fritz!Box to register its IP address.
 */
public class SetupDynDns extends BotRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.SetupDynDns} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.SetupDynDns create() {
		return new de.haumacher.phoneblock.ab.proto.SetupDynDns();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.SetupDynDns} type in JSON format. */
	public static final String SETUP_DYN_DNS__TYPE = "SetupDynDns";

	/** @see #getHostName() */
	public static final String HOST_NAME__PROP = "hostName";

	private String _hostName = "";

	/**
	 * Creates a {@link SetupDynDns} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.SetupDynDns#create()
	 */
	protected SetupDynDns() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.SETUP_DYN_DNS;
	}

	public final String getHostName() {
		return _hostName;
	}

	/**
	 * @see #getHostName()
	 */
	public de.haumacher.phoneblock.ab.proto.SetupDynDns setHostName(String value) {
		internalSetHostName(value);
		return this;
	}

	/** Internal setter for {@link #getHostName()} without chain call utility. */
	protected final void internalSetHostName(String value) {
		_hostName = value;
	}

	@Override
	public de.haumacher.phoneblock.ab.proto.SetupDynDns setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public String jsonType() {
		return SETUP_DYN_DNS__TYPE;
	}

	@SuppressWarnings("hiding")
	static final java.util.List<String> PROPERTIES;
	static {
		java.util.List<String> local = java.util.Arrays.asList(
			HOST_NAME__PROP);
		java.util.List<String> tmp = new java.util.ArrayList<>();
		tmp.addAll(de.haumacher.phoneblock.ab.proto.BotRequest.PROPERTIES);
		tmp.addAll(local);
		PROPERTIES = java.util.Collections.unmodifiableList(tmp);
	}

	@SuppressWarnings("hiding")
	static final java.util.Set<String> TRANSIENT_PROPERTIES;
	static {
		java.util.HashSet<String> tmp = new java.util.HashSet<>();
		tmp.addAll(de.haumacher.phoneblock.ab.proto.BotRequest.TRANSIENT_PROPERTIES);
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
			case HOST_NAME__PROP: return getHostName();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case HOST_NAME__PROP: internalSetHostName((String) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.SetupDynDns readSetupDynDns(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.SetupDynDns result = new de.haumacher.phoneblock.ab.proto.SetupDynDns();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(HOST_NAME__PROP);
		out.value(getHostName());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case HOST_NAME__PROP: setHostName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
