package de.haumacher.phoneblock.db.model;

/**
 * List of blocked numbers for retrieval through the <i>PhoneBlock API</i>.
 */
public class Blocklist extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link Blocklist} instance.
	 */
	public static Blocklist create() {
		return new de.haumacher.phoneblock.db.model.Blocklist();
	}

	/** Identifier for the {@link Blocklist} type in JSON format. */
	public static final String BLOCKLIST__TYPE = "Blocklist";

	/** @see #getNumbers() */
	public static final String NUMBERS__PROP = "numbers";

	private final java.util.List<PhoneInfo> _numbers = new de.haumacher.msgbuf.util.ReferenceList<PhoneInfo>() {
		@Override
		protected void beforeAdd(int index, PhoneInfo element) {
			_listener.beforeAdd(Blocklist.this, NUMBERS__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, PhoneInfo element) {
			_listener.afterRemove(Blocklist.this, NUMBERS__PROP, index, element);
		}
	};

	/**
	 * Creates a {@link Blocklist} instance.
	 *
	 * @see Blocklist#create()
	 */
	protected Blocklist() {
		super();
	}

	/**
	 * Numbers in the blocklist.
	 */
	public final java.util.List<PhoneInfo> getNumbers() {
		return _numbers;
	}

	/**
	 * @see #getNumbers()
	 */
	public Blocklist setNumbers(java.util.List<? extends PhoneInfo> value) {
		internalSetNumbers(value);
		return this;
	}

	/** Internal setter for {@link #getNumbers()} without chain call utility. */
	protected final void internalSetNumbers(java.util.List<? extends PhoneInfo> value) {
		if (value == null) throw new IllegalArgumentException("Property 'numbers' cannot be null.");
		_numbers.clear();
		_numbers.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getNumbers()} list.
	 */
	public Blocklist addNumber(PhoneInfo value) {
		internalAddNumber(value);
		return this;
	}

	/** Implementation of {@link #addNumber(PhoneInfo)} without chain call utility. */
	protected final void internalAddNumber(PhoneInfo value) {
		_numbers.add(value);
	}

	/**
	 * Removes a value from the {@link #getNumbers()} list.
	 */
	public final void removeNumber(PhoneInfo value) {
		_numbers.remove(value);
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public Blocklist registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public Blocklist unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return BLOCKLIST__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			NUMBERS__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case NUMBERS__PROP: return getNumbers();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case NUMBERS__PROP: internalSetNumbers(de.haumacher.msgbuf.util.Conversions.asList(PhoneInfo.class, value)); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static Blocklist readBlocklist(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.Blocklist result = new de.haumacher.phoneblock.db.model.Blocklist();
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
		out.name(NUMBERS__PROP);
		out.beginArray();
		for (PhoneInfo x : getNumbers()) {
			x.writeTo(out);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case NUMBERS__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addNumber(de.haumacher.phoneblock.db.model.PhoneInfo.readPhoneInfo(in));
				}
				in.endArray();
			}
			break;
			default: super.readField(in, field);
		}
	}

}
