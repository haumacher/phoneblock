package de.haumacher.phoneblock.db.settings;

/**
 * Account settings.
 */
public class UserSettings extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link UserSettings} instance.
	 */
	public static UserSettings create() {
		return new de.haumacher.phoneblock.db.settings.UserSettings();
	}

	/** Identifier for the {@link UserSettings} type in JSON format. */
	public static final String USER_SETTINGS__TYPE = "UserSettings";

	/** @see #getId() */
	public static final String ID__PROP = "id";

	/** @see #getMinVotes() */
	public static final String MIN_VOTES__PROP = "minVotes";

	/** @see #getMaxLength() */
	public static final String MAX_LENGTH__PROP = "maxLength";

	/** Identifier for the property {@link #getId()} in binary format. */
	static final int ID__ID = 1;

	/** Identifier for the property {@link #getMinVotes()} in binary format. */
	static final int MIN_VOTES__ID = 2;

	/** Identifier for the property {@link #getMaxLength()} in binary format. */
	static final int MAX_LENGTH__ID = 3;

	private long _id = 0L;

	private int _minVotes = 0;

	private int _maxLength = 0;

	/**
	 * Creates a {@link UserSettings} instance.
	 *
	 * @see UserSettings#create()
	 */
	protected UserSettings() {
		super();
	}

	/**
	 * The internal user ID.
	 */
	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public UserSettings setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_listener.beforeSet(this, ID__PROP, value);
		_id = value;
	}

	/**
	 * Minumum number of votes to create a blocklist entry.
	 */
	public final int getMinVotes() {
		return _minVotes;
	}

	/**
	 * @see #getMinVotes()
	 */
	public UserSettings setMinVotes(int value) {
		internalSetMinVotes(value);
		return this;
	}

	/** Internal setter for {@link #getMinVotes()} without chain call utility. */
	protected final void internalSetMinVotes(int value) {
		_listener.beforeSet(this, MIN_VOTES__PROP, value);
		_minVotes = value;
	}

	/**
	 * Maximum number of blocklist entries.
	 */
	public final int getMaxLength() {
		return _maxLength;
	}

	/**
	 * @see #getMaxLength()
	 */
	public UserSettings setMaxLength(int value) {
		internalSetMaxLength(value);
		return this;
	}

	/** Internal setter for {@link #getMaxLength()} without chain call utility. */
	protected final void internalSetMaxLength(int value) {
		_listener.beforeSet(this, MAX_LENGTH__PROP, value);
		_maxLength = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public UserSettings registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public UserSettings unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return USER_SETTINGS__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ID__PROP, 
			MIN_VOTES__PROP, 
			MAX_LENGTH__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ID__PROP: return getId();
			case MIN_VOTES__PROP: return getMinVotes();
			case MAX_LENGTH__PROP: return getMaxLength();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ID__PROP: internalSetId((long) value); break;
			case MIN_VOTES__PROP: internalSetMinVotes((int) value); break;
			case MAX_LENGTH__PROP: internalSetMaxLength((int) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static UserSettings readUserSettings(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.UserSettings result = new de.haumacher.phoneblock.db.settings.UserSettings();
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
		out.name(MIN_VOTES__PROP);
		out.value(getMinVotes());
		out.name(MAX_LENGTH__PROP);
		out.value(getMaxLength());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(in.nextLong()); break;
			case MIN_VOTES__PROP: setMinVotes(in.nextInt()); break;
			case MAX_LENGTH__PROP: setMaxLength(in.nextInt()); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.beginObject();
		writeFields(out);
		out.endObject();
	}

	/**
	 * Serializes all fields of this instance to the given binary output.
	 *
	 * @param out
	 *        The binary output to write to.
	 * @throws java.io.IOException If writing fails.
	 */
	protected void writeFields(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.name(ID__ID);
		out.value(getId());
		out.name(MIN_VOTES__ID);
		out.value(getMinVotes());
		out.name(MAX_LENGTH__ID);
		out.value(getMaxLength());
	}

	/** Reads a new instance from the given reader. */
	public static UserSettings readUserSettings(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		UserSettings result = de.haumacher.phoneblock.db.settings.UserSettings.readUserSettings_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link UserSettings} from a polymorphic composition. */
	public static UserSettings readUserSettings_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.UserSettings result = new UserSettings();
		result.readContent(in);
		return result;
	}

	/** Helper for reading all fields of this instance. */
	protected final void readContent(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		while (in.hasNext()) {
			int field = in.nextName();
			readField(in, field);
		}
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case ID__ID: setId(in.nextLong()); break;
			case MIN_VOTES__ID: setMinVotes(in.nextInt()); break;
			case MAX_LENGTH__ID: setMaxLength(in.nextInt()); break;
			default: in.skipValue(); 
		}
	}

}
