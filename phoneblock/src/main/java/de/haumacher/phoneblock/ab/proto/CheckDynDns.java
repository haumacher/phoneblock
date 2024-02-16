package de.haumacher.phoneblock.ab.proto;

/**
 * Checks, whether a DynDNS request has been received.
 */
public class CheckDynDns extends BotRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.CheckDynDns} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.CheckDynDns create() {
		return new de.haumacher.phoneblock.ab.proto.CheckDynDns();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.CheckDynDns} type in JSON format. */
	public static final String CHECK_DYN_DNS__TYPE = "CheckDynDns";

	/**
	 * Creates a {@link CheckDynDns} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.CheckDynDns#create()
	 */
	protected CheckDynDns() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.CHECK_DYN_DNS;
	}

	@Override
	public de.haumacher.phoneblock.ab.proto.CheckDynDns setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public String jsonType() {
		return CHECK_DYN_DNS__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.CheckDynDns readCheckDynDns(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.CheckDynDns result = new de.haumacher.phoneblock.ab.proto.CheckDynDns();
		result.readContent(in);
		return result;
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
