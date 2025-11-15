package de.haumacher.phoneblock.ab.proto;

/**
 * Sets the retention policy for automatic call cleanup.
 */
public class SetRetentionPolicy extends BotRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.SetRetentionPolicy} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.SetRetentionPolicy create() {
		return new de.haumacher.phoneblock.ab.proto.SetRetentionPolicy();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.SetRetentionPolicy} type in JSON format. */
	public static final String SET_RETENTION_POLICY__TYPE = "SetRetentionPolicy";

	/** @see #getPeriod() */
	public static final String PERIOD__PROP = "period";

	private de.haumacher.phoneblock.ab.proto.RetentionPeriod _period = de.haumacher.phoneblock.ab.proto.RetentionPeriod.NEVER;

	/**
	 * Creates a {@link SetRetentionPolicy} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.SetRetentionPolicy#create()
	 */
	protected SetRetentionPolicy() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.SET_RETENTION_POLICY;
	}

	/**
	 * The retention period (NEVER, WEEK, MONTH, QUARTER, YEAR).
	 */
	public final de.haumacher.phoneblock.ab.proto.RetentionPeriod getPeriod() {
		return _period;
	}

	/**
	 * @see #getPeriod()
	 */
	public de.haumacher.phoneblock.ab.proto.SetRetentionPolicy setPeriod(de.haumacher.phoneblock.ab.proto.RetentionPeriod value) {
		internalSetPeriod(value);
		return this;
	}

	/** Internal setter for {@link #getPeriod()} without chain call utility. */
	protected final void internalSetPeriod(de.haumacher.phoneblock.ab.proto.RetentionPeriod value) {
		if (value == null) throw new IllegalArgumentException("Property 'period' cannot be null.");
		_period = value;
	}

	@Override
	public de.haumacher.phoneblock.ab.proto.SetRetentionPolicy setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public String jsonType() {
		return SET_RETENTION_POLICY__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			PERIOD__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case PERIOD__PROP: return getPeriod();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case PERIOD__PROP: internalSetPeriod((de.haumacher.phoneblock.ab.proto.RetentionPeriod) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.SetRetentionPolicy readSetRetentionPolicy(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.SetRetentionPolicy result = new de.haumacher.phoneblock.ab.proto.SetRetentionPolicy();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(PERIOD__PROP);
		getPeriod().writeTo(out);
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PERIOD__PROP: setPeriod(de.haumacher.phoneblock.ab.proto.RetentionPeriod.readRetentionPeriod(in)); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
