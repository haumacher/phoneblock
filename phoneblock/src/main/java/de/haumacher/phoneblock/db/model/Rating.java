package de.haumacher.phoneblock.db.model;

/**
 * A classification of phone calls.
 */
public enum Rating implements de.haumacher.msgbuf.data.ProtocolEnum {

	/**
	 * A regular non-spam call.
	 */
	A_LEGITIMATE("A_LEGITIMATE"),

	/**
	 * The user has missed the call and cannot decide, whether it was spam.
	 */
	B_MISSED("B_MISSED"),

	/**
	 * The caller immediately cut the connection.
	 */
	C_PING("C_PING"),

	/**
	 * A poll.
	 */
	D_POLL("D_POLL"),

	/**
	 * Some form of advertising, marketing unwanted consulting.
	 */
	E_ADVERTISING("E_ADVERTISING"),

	/**
	 * Some form of gambling or notice of prize notification.
	 */
	F_GAMBLE("F_GAMBLE"),

	/**
	 * Some form of fraud.
	 */
	G_FRAUD("G_FRAUD"),

	;

	private final String _protocolName;

	private Rating(String protocolName) {
		_protocolName = protocolName;
	}

	/**
	 * The protocol name of a {@link Rating} constant.
	 *
	 * @see #valueOfProtocol(String)
	 */
	@Override
	public String protocolName() {
		return _protocolName;
	}

	/** Looks up a {@link Rating} constant by it's protocol name. */
	public static Rating valueOfProtocol(String protocolName) {
		if (protocolName == null) { return null; }
		switch (protocolName) {
			case "A_LEGITIMATE": return A_LEGITIMATE;
			case "B_MISSED": return B_MISSED;
			case "C_PING": return C_PING;
			case "D_POLL": return D_POLL;
			case "E_ADVERTISING": return E_ADVERTISING;
			case "F_GAMBLE": return F_GAMBLE;
			case "G_FRAUD": return G_FRAUD;
		}
		return A_LEGITIMATE;
	}

	/** Writes this instance to the given output. */
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		out.value(protocolName());
	}

	/** Reads a new instance from the given reader. */
	public static Rating readRating(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		return valueOfProtocol(in.nextString());
	}

	/** Writes this instance to the given binary output. */
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		switch (this) {
			case A_LEGITIMATE: out.value(1); break;
			case B_MISSED: out.value(2); break;
			case C_PING: out.value(3); break;
			case D_POLL: out.value(4); break;
			case E_ADVERTISING: out.value(5); break;
			case F_GAMBLE: out.value(6); break;
			case G_FRAUD: out.value(7); break;
			default: out.value(0);
		}
	}

	/** Reads a new instance from the given binary reader. */
	public static Rating readRating(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		switch (in.nextInt()) {
			case 1: return A_LEGITIMATE;
			case 2: return B_MISSED;
			case 3: return C_PING;
			case 4: return D_POLL;
			case 5: return E_ADVERTISING;
			case 6: return F_GAMBLE;
			case 7: return G_FRAUD;
			default: return A_LEGITIMATE;
		}
	}
}
