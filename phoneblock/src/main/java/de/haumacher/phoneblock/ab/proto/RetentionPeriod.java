package de.haumacher.phoneblock.ab.proto;

/**
 * Specification when old calls should be deleted automatically.
 */
public enum RetentionPeriod implements de.haumacher.msgbuf.data.ProtocolEnum {

	/**
	 * Never delete calls automatically
	 */
	NEVER("NEVER"),

	/**
	 * Delete calls older than one week
	 */
	WEEK("WEEK"),

	/**
	 * Delete calls older than one month
	 */
	MONTH("MONTH"),

	/**
	 * Delete calls older than three months
	 */
	QUARTER("QUARTER"),

	/**
	 * Delete calls older than one year
	 */
	YEAR("YEAR"),

	;

	private final String _protocolName;

	private RetentionPeriod(String protocolName) {
		_protocolName = protocolName;
	}

	/**
	 * The protocol name of a {@link RetentionPeriod} constant.
	 *
	 * @see #valueOfProtocol(String)
	 */
	@Override
	public String protocolName() {
		return _protocolName;
	}

	/** Looks up a {@link RetentionPeriod} constant by it's protocol name. */
	public static RetentionPeriod valueOfProtocol(String protocolName) {
		if (protocolName == null) { return null; }
		switch (protocolName) {
			case "NEVER": return NEVER;
			case "WEEK": return WEEK;
			case "MONTH": return MONTH;
			case "QUARTER": return QUARTER;
			case "YEAR": return YEAR;
		}
		return NEVER;
	}

	/** Writes this instance to the given output. */
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		out.value(protocolName());
	}

	/** Reads a new instance from the given reader. */
	public static RetentionPeriod readRetentionPeriod(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		return valueOfProtocol(in.nextString());
	}

	/** Writes this instance to the given binary output. */
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		switch (this) {
			case NEVER: out.value(1); break;
			case WEEK: out.value(2); break;
			case MONTH: out.value(3); break;
			case QUARTER: out.value(4); break;
			case YEAR: out.value(5); break;
			default: out.value(0);
		}
	}

	/** Reads a new instance from the given binary reader. */
	public static RetentionPeriod readRetentionPeriod(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		switch (in.nextInt()) {
			case 1: return NEVER;
			case 2: return WEEK;
			case 3: return MONTH;
			case 4: return QUARTER;
			case 5: return YEAR;
			default: return NEVER;
		}
	}
}
