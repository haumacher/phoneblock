package de.haumacher.phoneblock.analysis;

public class AreaCode extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link AreaCode} instance.
	 */
	public static AreaCode create() {
		return new AreaCode();
	}

	/** Identifier for the {@link AreaCode} type in JSON format. */
	public static final String AREA_CODE__TYPE = "AreaCode";

	/** @see #getPhoneAreaCode() */
	public static final String PHONE_AREA_CODE = "phone_area_code";

	/** @see #getCity() */
	public static final String CITY = "city";

	/** @see #isActive() */
	public static final String ACTIVE = "active";

	/** Identifier for the property {@link #getPhoneAreaCode()} in binary format. */
	public static final int PHONE_AREA_CODE__ID = 1;

	/** Identifier for the property {@link #getCity()} in binary format. */
	public static final int CITY__ID = 2;

	/** Identifier for the property {@link #isActive()} in binary format. */
	public static final int ACTIVE__ID = 3;

	private String _phoneAreaCode = "";

	private String _city = "";

	private boolean _active = false;

	/**
	 * Creates a {@link AreaCode} instance.
	 *
	 * @see #create()
	 */
	protected AreaCode() {
		super();
	}

	public final String getPhoneAreaCode() {
		return _phoneAreaCode;
	}

	/**
	 * @see #getPhoneAreaCode()
	 */
	public AreaCode setPhoneAreaCode(String value) {
		internalSetPhoneAreaCode(value);
		return this;
	}
	/** Internal setter for {@link #getPhoneAreaCode()} without chain call utility. */
	protected final void internalSetPhoneAreaCode(String value) {
		_listener.beforeSet(this, PHONE_AREA_CODE, value);
		_phoneAreaCode = value;
	}


	public final String getCity() {
		return _city;
	}

	/**
	 * @see #getCity()
	 */
	public AreaCode setCity(String value) {
		internalSetCity(value);
		return this;
	}
	/** Internal setter for {@link #getCity()} without chain call utility. */
	protected final void internalSetCity(String value) {
		_listener.beforeSet(this, CITY, value);
		_city = value;
	}


	public final boolean isActive() {
		return _active;
	}

	/**
	 * @see #isActive()
	 */
	public AreaCode setActive(boolean value) {
		internalSetActive(value);
		return this;
	}
	/** Internal setter for {@link #isActive()} without chain call utility. */
	protected final void internalSetActive(boolean value) {
		_listener.beforeSet(this, ACTIVE, value);
		_active = value;
	}


	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public AreaCode registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public AreaCode unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return AREA_CODE__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			PHONE_AREA_CODE, 
			CITY, 
			ACTIVE));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case PHONE_AREA_CODE: return getPhoneAreaCode();
			case CITY: return getCity();
			case ACTIVE: return isActive();
			default: return de.haumacher.msgbuf.observer.Observable.super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case PHONE_AREA_CODE: setPhoneAreaCode((String) value); break;
			case CITY: setCity((String) value); break;
			case ACTIVE: setActive((boolean) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static AreaCode readAreaCode(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		AreaCode result = new AreaCode();
		in.beginObject();
		result.readFields(in);
		in.endObject();
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		writeContent(out);
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(PHONE_AREA_CODE);
		out.value(getPhoneAreaCode());
		out.name(CITY);
		out.value(getCity());
		out.name(ACTIVE);
		out.value(isActive());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE_AREA_CODE: setPhoneAreaCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CITY: setCity(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ACTIVE: setActive(in.nextBoolean()); break;
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
		out.name(PHONE_AREA_CODE__ID);
		out.value(getPhoneAreaCode());
		out.name(CITY__ID);
		out.value(getCity());
		out.name(ACTIVE__ID);
		out.value(isActive());
	}

	/** Reads a new instance from the given reader. */
	public static AreaCode readAreaCode(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		AreaCode result = new AreaCode();
		while (in.hasNext()) {
			int field = in.nextName();
			result.readField(in, field);
		}
		in.endObject();
		return result;
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case PHONE_AREA_CODE__ID: setPhoneAreaCode(in.nextString()); break;
			case CITY__ID: setCity(in.nextString()); break;
			case ACTIVE__ID: setActive(in.nextBoolean()); break;
			default: in.skipValue(); 
		}
	}

}
