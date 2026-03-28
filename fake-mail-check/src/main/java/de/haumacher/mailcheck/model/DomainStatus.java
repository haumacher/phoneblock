package de.haumacher.mailcheck.model;

/**
 * Classification status of an e-mail domain.
 */
public enum DomainStatus implements de.haumacher.msgbuf.data.ProtocolEnum {

	/**
	 * Domain provides disposable/temporary e-mail addresses.
	 */
	DISPOSABLE("DISPOSABLE"),

	/**
	 * Domain is a legitimate e-mail provider.
	 */
	SAFE("SAFE"),

	/**
	 * Domain has no valid MX record and cannot receive e-mail.
	 */
	INVALID("INVALID"),

	;

	private final String _protocolName;

	private DomainStatus(String protocolName) {
		_protocolName = protocolName;
	}

	/**
	 * The protocol name of a {@link DomainStatus} constant.
	 *
	 * @see #valueOfProtocol(String)
	 */
	@Override
	public String protocolName() {
		return _protocolName;
	}

	/** Looks up a {@link DomainStatus} constant by it's protocol name. */
	public static DomainStatus valueOfProtocol(String protocolName) {
		if (protocolName == null) { return null; }
		switch (protocolName) {
			case "DISPOSABLE": return DISPOSABLE;
			case "SAFE": return SAFE;
			case "INVALID": return INVALID;
		}
		return DISPOSABLE;
	}

	/** Writes this instance to the given output. */
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		out.value(protocolName());
	}

	/** Reads a new instance from the given reader. */
	public static DomainStatus readDomainStatus(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		return valueOfProtocol(in.nextString());
	}

	/** Writes this instance to the given binary output. */
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		switch (this) {
			case DISPOSABLE: out.value(1); break;
			case SAFE: out.value(2); break;
			case INVALID: out.value(3); break;
			default: out.value(0);
		}
	}

	/** Reads a new instance from the given binary reader. */
	public static DomainStatus readDomainStatus(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		switch (in.nextInt()) {
			case 1: return DISPOSABLE;
			case 2: return SAFE;
			case 3: return INVALID;
			default: return DISPOSABLE;
		}
	}
}
