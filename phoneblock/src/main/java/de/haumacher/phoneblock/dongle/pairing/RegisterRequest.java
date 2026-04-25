package de.haumacher.phoneblock.dongle.pairing;

/**
 * Body of POST /api/dongle/register, sent by the dongle once at boot when
 * its "pairing" partition holds a valid secret. Lets the install page on
 * phoneblock.net learn this dongle's LAN IP without depending on
 * mDNS/Fritz!Box host-name resolution.
 */
public class RegisterRequest extends de.haumacher.msgbuf.data.AbstractDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.dongle.pairing.RegisterRequest} instance.
	 */
	public static de.haumacher.phoneblock.dongle.pairing.RegisterRequest create() {
		return new de.haumacher.phoneblock.dongle.pairing.RegisterRequest();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.dongle.pairing.RegisterRequest} type in JSON format. */
	public static final String REGISTER_REQUEST__TYPE = "RegisterRequest";

	/** @see #getSecret() */
	private static final String SECRET__PROP = "secret";

	/** @see #getLanIp() */
	private static final String LAN_IP__PROP = "lanIp";

	private String _secret = "";

	private String _lanIp = "";

	/**
	 * Creates a {@link RegisterRequest} instance.
	 *
	 * @see de.haumacher.phoneblock.dongle.pairing.RegisterRequest#create()
	 */
	protected RegisterRequest() {
		super();
	}

	/**
	 * 16-byte session secret as 32 lowercase-hex characters.
	 */
	public final String getSecret() {
		return _secret;
	}

	/**
	 * @see #getSecret()
	 */
	public de.haumacher.phoneblock.dongle.pairing.RegisterRequest setSecret(String value) {
		internalSetSecret(value);
		return this;
	}

	/** Internal setter for {@link #getSecret()} without chain call utility. */
	protected final void internalSetSecret(String value) {
		_secret = value;
	}

	/**
	 * The dongle's IPv4 address inside the user's LAN, dotted-quad. The
	 * server stores this verbatim and replays it to the install page on
	 * lookup, which then navigates the user's browser there.
	 */
	public final String getLanIp() {
		return _lanIp;
	}

	/**
	 * @see #getLanIp()
	 */
	public de.haumacher.phoneblock.dongle.pairing.RegisterRequest setLanIp(String value) {
		internalSetLanIp(value);
		return this;
	}

	/** Internal setter for {@link #getLanIp()} without chain call utility. */
	protected final void internalSetLanIp(String value) {
		_lanIp = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.dongle.pairing.RegisterRequest readRegisterRequest(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.dongle.pairing.RegisterRequest result = new de.haumacher.phoneblock.dongle.pairing.RegisterRequest();
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
		out.name(SECRET__PROP);
		out.value(getSecret());
		out.name(LAN_IP__PROP);
		out.value(getLanIp());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case SECRET__PROP: setSecret(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case LAN_IP__PROP: setLanIp(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

}
