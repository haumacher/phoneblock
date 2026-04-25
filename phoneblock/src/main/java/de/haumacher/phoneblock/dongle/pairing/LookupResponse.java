package de.haumacher.phoneblock.dongle.pairing;

/**
 * Body of GET /api/dongle/lookup when a matching registration exists.
 * Returns 404 with no body if the secret is unknown or expired.
 */
public class LookupResponse extends de.haumacher.msgbuf.data.AbstractDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.dongle.pairing.LookupResponse} instance.
	 */
	public static de.haumacher.phoneblock.dongle.pairing.LookupResponse create() {
		return new de.haumacher.phoneblock.dongle.pairing.LookupResponse();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.dongle.pairing.LookupResponse} type in JSON format. */
	public static final String LOOKUP_RESPONSE__TYPE = "LookupResponse";

	/** @see #getLanIp() */
	private static final String LAN_IP__PROP = "lanIp";

	private String _lanIp = "";

	/**
	 * Creates a {@link LookupResponse} instance.
	 *
	 * @see de.haumacher.phoneblock.dongle.pairing.LookupResponse#create()
	 */
	protected LookupResponse() {
		super();
	}

	/**
	 * Same value the dongle sent as RegisterRequest.lanIp.
	 */
	public final String getLanIp() {
		return _lanIp;
	}

	/**
	 * @see #getLanIp()
	 */
	public de.haumacher.phoneblock.dongle.pairing.LookupResponse setLanIp(String value) {
		internalSetLanIp(value);
		return this;
	}

	/** Internal setter for {@link #getLanIp()} without chain call utility. */
	protected final void internalSetLanIp(String value) {
		_lanIp = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.dongle.pairing.LookupResponse readLookupResponse(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.dongle.pairing.LookupResponse result = new de.haumacher.phoneblock.dongle.pairing.LookupResponse();
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
		out.name(LAN_IP__PROP);
		out.value(getLanIp());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case LAN_IP__PROP: setLanIp(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

}
