package de.haumacher.phoneblock.analysis;

public class AreaCode extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.analysis.AreaCode} instance.
	 */
	public static de.haumacher.phoneblock.analysis.AreaCode create() {
		return new de.haumacher.phoneblock.analysis.AreaCode();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.analysis.AreaCode} type in JSON format. */
	public static final String AREA_CODE__TYPE = "AreaCode";

	/** @see #getPhoneAreaCode() */
	public static final String PHONE_AREA_CODE__PROP = "phone_area_code";

	/** @see #getCity() */
	public static final String CITY__PROP = "city";

	/** @see #isActive() */
	public static final String ACTIVE__PROP = "active";

	/** Identifier for the property {@link #getPhoneAreaCode()} in binary format. */
	static final int PHONE_AREA_CODE__ID = 1;

	/** Identifier for the property {@link #getCity()} in binary format. */
	static final int CITY__ID = 2;

	/** Identifier for the property {@link #isActive()} in binary format. */
	static final int ACTIVE__ID = 3;

	private String _phoneAreaCode = "";

	private String _city = "";

	private boolean _active = false;

	/**
	 * Creates a {@link AreaCode} instance.
	 *
	 * @see de.haumacher.phoneblock.analysis.AreaCode#create()
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
	public de.haumacher.phoneblock.analysis.AreaCode setPhoneAreaCode(String value) {
		internalSetPhoneAreaCode(value);
		return this;
	}

	/** Internal setter for {@link #getPhoneAreaCode()} without chain call utility. */
	protected final void internalSetPhoneAreaCode(String value) {
		_listener.beforeSet(this, PHONE_AREA_CODE__PROP, value);
		_phoneAreaCode = value;
	}

	public final String getCity() {
		return _city;
	}

	/**
	 * @see #getCity()
	 */
	public de.haumacher.phoneblock.analysis.AreaCode setCity(String value) {
		internalSetCity(value);
		return this;
	}

	/** Internal setter for {@link #getCity()} without chain call utility. */
	protected final void internalSetCity(String value) {
		_listener.beforeSet(this, CITY__PROP, value);
		_city = value;
	}

	public final boolean isActive() {
		return _active;
	}

	/**
	 * @see #isActive()
	 */
	public de.haumacher.phoneblock.analysis.AreaCode setActive(boolean value) {
		internalSetActive(value);
		return this;
	}

	/** Internal setter for {@link #isActive()} without chain call utility. */
	protected final void internalSetActive(boolean value) {
		_listener.beforeSet(this, ACTIVE__PROP, value);
		_active = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.analysis.AreaCode registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.analysis.AreaCode unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
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
			PHONE_AREA_CODE__PROP, 
			CITY__PROP, 
			ACTIVE__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case PHONE_AREA_CODE__PROP: return getPhoneAreaCode();
			case CITY__PROP: return getCity();
			case ACTIVE__PROP: return isActive();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case PHONE_AREA_CODE__PROP: internalSetPhoneAreaCode((String) value); break;
			case CITY__PROP: internalSetCity((String) value); break;
			case ACTIVE__PROP: internalSetActive((boolean) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.analysis.AreaCode readAreaCode(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.analysis.AreaCode result = new de.haumacher.phoneblock.analysis.AreaCode();
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
		out.name(PHONE_AREA_CODE__PROP);
		out.value(getPhoneAreaCode());
		out.name(CITY__PROP);
		out.value(getCity());
		out.name(ACTIVE__PROP);
		out.value(isActive());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE_AREA_CODE__PROP: setPhoneAreaCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CITY__PROP: setCity(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ACTIVE__PROP: setActive(in.nextBoolean()); break;
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
	public static de.haumacher.phoneblock.analysis.AreaCode readAreaCode(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.analysis.AreaCode result = de.haumacher.phoneblock.analysis.AreaCode.readAreaCode_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.analysis.AreaCode} from a polymorphic composition. */
	public static de.haumacher.phoneblock.analysis.AreaCode readAreaCode_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.analysis.AreaCode result = new AreaCode();
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
			case PHONE_AREA_CODE__ID: setPhoneAreaCode(in.nextString()); break;
			case CITY__ID: setCity(in.nextString()); break;
			case ACTIVE__ID: setActive(in.nextBoolean()); break;
			default: in.skipValue(); 
		}
	}

}
