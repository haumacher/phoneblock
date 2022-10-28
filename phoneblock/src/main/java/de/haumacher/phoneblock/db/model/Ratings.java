package de.haumacher.phoneblock.db.model;

public class Ratings extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link Ratings} instance.
	 */
	public static Ratings create() {
		return new de.haumacher.phoneblock.db.model.Ratings();
	}

	/** Identifier for the {@link Ratings} type in JSON format. */
	public static final String RATINGS__TYPE = "Ratings";

	/** @see #getValues() */
	public static final String VALUES__PROP = "values";

	private final java.util.List<Rating> _values = new de.haumacher.msgbuf.util.ReferenceList<Rating>() {
		@Override
		protected void beforeAdd(int index, Rating element) {
			_listener.beforeAdd(Ratings.this, VALUES__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, Rating element) {
			_listener.afterRemove(Ratings.this, VALUES__PROP, index, element);
		}
	};

	/**
	 * Creates a {@link Ratings} instance.
	 *
	 * @see Ratings#create()
	 */
	protected Ratings() {
		super();
	}

	public final java.util.List<Rating> getValues() {
		return _values;
	}

	/**
	 * @see #getValues()
	 */
	public Ratings setValues(java.util.List<? extends Rating> value) {
		internalSetValues(value);
		return this;
	}

	/** Internal setter for {@link #getValues()} without chain call utility. */
	protected final void internalSetValues(java.util.List<? extends Rating> value) {
		if (value == null) throw new IllegalArgumentException("Property 'values' cannot be null.");
		_values.clear();
		_values.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getValues()} list.
	 */
	public Ratings addValue(Rating value) {
		internalAddValue(value);
		return this;
	}

	/** Implementation of {@link #addValue(Rating)} without chain call utility. */
	protected final void internalAddValue(Rating value) {
		_values.add(value);
	}

	/**
	 * Removes a value from the {@link #getValues()} list.
	 */
	public final void removeValue(Rating value) {
		_values.remove(value);
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public Ratings registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public Ratings unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return RATINGS__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			VALUES__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case VALUES__PROP: return getValues();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case VALUES__PROP: internalSetValues(de.haumacher.msgbuf.util.Conversions.asList(Rating.class, value)); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static Ratings readRatings(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.Ratings result = new de.haumacher.phoneblock.db.model.Ratings();
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
		out.name(VALUES__PROP);
		out.beginArray();
		for (Rating x : getValues()) {
			x.writeTo(out);
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case VALUES__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addValue(de.haumacher.phoneblock.db.model.Rating.readRating(in));
				}
				in.endArray();
			}
			break;
			default: super.readField(in, field);
		}
	}

}
