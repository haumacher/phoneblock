package de.haumacher.phoneblock.ab.proto;

/**
 * Switches the answer bot on.
 */
public class UpdateAnswerBot extends BotRequest {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.UpdateAnswerBot} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.UpdateAnswerBot create() {
		return new de.haumacher.phoneblock.ab.proto.UpdateAnswerBot();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.UpdateAnswerBot} type in JSON format. */
	public static final String UPDATE_ANSWER_BOT__TYPE = "UpdateAnswerBot";

	/** @see #isEnabled() */
	public static final String ENABLED__PROP = "enabled";

	/** @see #isPreferIPv4() */
	public static final String PREFER_IPV_4__PROP = "preferIPv4";

	/** @see #getMinVotes() */
	public static final String MIN_VOTES__PROP = "minVotes";

	/** @see #isWildcards() */
	public static final String WILDCARDS__PROP = "wildcards";

	private boolean _enabled = false;

	private boolean _preferIPv4 = false;

	private int _minVotes = 0;

	private boolean _wildcards = false;

	/**
	 * Creates a {@link UpdateAnswerBot} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.UpdateAnswerBot#create()
	 */
	protected UpdateAnswerBot() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.UPDATE_ANSWER_BOT;
	}

	/**
	 * Whether the bot is enabled (registration is active).
	 */
	public final boolean isEnabled() {
		return _enabled;
	}

	/**
	 * @see #isEnabled()
	 */
	public de.haumacher.phoneblock.ab.proto.UpdateAnswerBot setEnabled(boolean value) {
		internalSetEnabled(value);
		return this;
	}

	/** Internal setter for {@link #isEnabled()} without chain call utility. */
	protected final void internalSetEnabled(boolean value) {
		_enabled = value;
	}

	/**
	 * Whether to limit communication to IPv4.
	 */
	public final boolean isPreferIPv4() {
		return _preferIPv4;
	}

	/**
	 * @see #isPreferIPv4()
	 */
	public de.haumacher.phoneblock.ab.proto.UpdateAnswerBot setPreferIPv4(boolean value) {
		internalSetPreferIPv4(value);
		return this;
	}

	/** Internal setter for {@link #isPreferIPv4()} without chain call utility. */
	protected final void internalSetPreferIPv4(boolean value) {
		_preferIPv4 = value;
	}

	/**
	 * The minimum PhoneBlock votes to consider a call as SPAM and accept it.
	 */
	public final int getMinVotes() {
		return _minVotes;
	}

	/**
	 * @see #getMinVotes()
	 */
	public de.haumacher.phoneblock.ab.proto.UpdateAnswerBot setMinVotes(int value) {
		internalSetMinVotes(value);
		return this;
	}

	/** Internal setter for {@link #getMinVotes()} without chain call utility. */
	protected final void internalSetMinVotes(int value) {
		_minVotes = value;
	}

	/**
	 * Whether to block whole number ranges, when a great density of nearby SPAM numbers is detected.
	 */
	public final boolean isWildcards() {
		return _wildcards;
	}

	/**
	 * @see #isWildcards()
	 */
	public de.haumacher.phoneblock.ab.proto.UpdateAnswerBot setWildcards(boolean value) {
		internalSetWildcards(value);
		return this;
	}

	/** Internal setter for {@link #isWildcards()} without chain call utility. */
	protected final void internalSetWildcards(boolean value) {
		_wildcards = value;
	}

	@Override
	public de.haumacher.phoneblock.ab.proto.UpdateAnswerBot setId(long value) {
		internalSetId(value);
		return this;
	}

	@Override
	public String jsonType() {
		return UPDATE_ANSWER_BOT__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ENABLED__PROP, 
			PREFER_IPV_4__PROP, 
			MIN_VOTES__PROP, 
			WILDCARDS__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ENABLED__PROP: return isEnabled();
			case PREFER_IPV_4__PROP: return isPreferIPv4();
			case MIN_VOTES__PROP: return getMinVotes();
			case WILDCARDS__PROP: return isWildcards();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ENABLED__PROP: internalSetEnabled((boolean) value); break;
			case PREFER_IPV_4__PROP: internalSetPreferIPv4((boolean) value); break;
			case MIN_VOTES__PROP: internalSetMinVotes((int) value); break;
			case WILDCARDS__PROP: internalSetWildcards((boolean) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.ab.proto.UpdateAnswerBot readUpdateAnswerBot(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.ab.proto.UpdateAnswerBot result = new de.haumacher.phoneblock.ab.proto.UpdateAnswerBot();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(ENABLED__PROP);
		out.value(isEnabled());
		out.name(PREFER_IPV_4__PROP);
		out.value(isPreferIPv4());
		out.name(MIN_VOTES__PROP);
		out.value(getMinVotes());
		out.name(WILDCARDS__PROP);
		out.value(isWildcards());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ENABLED__PROP: setEnabled(in.nextBoolean()); break;
			case PREFER_IPV_4__PROP: setPreferIPv4(in.nextBoolean()); break;
			case MIN_VOTES__PROP: setMinVotes(in.nextInt()); break;
			case WILDCARDS__PROP: setWildcards(in.nextBoolean()); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.ab.proto.BotRequest.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
