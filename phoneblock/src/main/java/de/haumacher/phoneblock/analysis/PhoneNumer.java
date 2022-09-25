package de.haumacher.phoneblock.analysis;

public class PhoneNumer extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link PhoneNumer} instance.
	 */
	public static PhoneNumer create() {
		return new PhoneNumer();
	}

	/** Identifier for the {@link PhoneNumer} type in JSON format. */
	public static final String PHONE_NUMER__TYPE = "PhoneNumer";

	/** @see #getShortcut() */
	public static final String SHORTCUT = "shortcut";

	/** @see #getPlus() */
	public static final String PLUS = "plus";

	/** @see #getZeroZero() */
	public static final String ZERO_ZERO = "zeroZero";

	/** @see #getCountryCode() */
	public static final String COUNTRY_CODE = "countryCode";

	/** @see #getCountry() */
	public static final String COUNTRY = "country";

	/** @see #getCityCode() */
	public static final String CITY_CODE = "cityCode";

	/** @see #getCity() */
	public static final String CITY = "city";

	/** Identifier for the property {@link #getShortcut()} in binary format. */
	public static final int SHORTCUT__ID = 1;

	/** Identifier for the property {@link #getPlus()} in binary format. */
	public static final int PLUS__ID = 2;

	/** Identifier for the property {@link #getZeroZero()} in binary format. */
	public static final int ZERO_ZERO__ID = 3;

	/** Identifier for the property {@link #getCountryCode()} in binary format. */
	public static final int COUNTRY_CODE__ID = 4;

	/** Identifier for the property {@link #getCountry()} in binary format. */
	public static final int COUNTRY__ID = 5;

	/** Identifier for the property {@link #getCityCode()} in binary format. */
	public static final int CITY_CODE__ID = 6;

	/** Identifier for the property {@link #getCity()} in binary format. */
	public static final int CITY__ID = 7;

	private String _shortcut = null;

	private String _plus = "";

	private String _zeroZero = "";

	private String _countryCode = "";

	private String _country = "";

	private String _cityCode = null;

	private String _city = null;

	/**
	 * Creates a {@link PhoneNumer} instance.
	 *
	 * @see #create()
	 */
	protected PhoneNumer() {
		super();
	}

	public final String getShortcut() {
		return _shortcut;
	}

	/**
	 * @see #getShortcut()
	 */
	public PhoneNumer setShortcut(String value) {
		internalSetShortcut(value);
		return this;
	}
	/** Internal setter for {@link #getShortcut()} without chain call utility. */
	protected final void internalSetShortcut(String value) {
		_listener.beforeSet(this, SHORTCUT, value);
		_shortcut = value;
	}


	/**
	 * Checks, whether {@link #getShortcut()} has a value.
	 */
	public final boolean hasShortcut() {
		return _shortcut != null;
	}

	public final String getPlus() {
		return _plus;
	}

	/**
	 * @see #getPlus()
	 */
	public PhoneNumer setPlus(String value) {
		internalSetPlus(value);
		return this;
	}
	/** Internal setter for {@link #getPlus()} without chain call utility. */
	protected final void internalSetPlus(String value) {
		_listener.beforeSet(this, PLUS, value);
		_plus = value;
	}


	public final String getZeroZero() {
		return _zeroZero;
	}

	/**
	 * @see #getZeroZero()
	 */
	public PhoneNumer setZeroZero(String value) {
		internalSetZeroZero(value);
		return this;
	}
	/** Internal setter for {@link #getZeroZero()} without chain call utility. */
	protected final void internalSetZeroZero(String value) {
		_listener.beforeSet(this, ZERO_ZERO, value);
		_zeroZero = value;
	}


	public final String getCountryCode() {
		return _countryCode;
	}

	/**
	 * @see #getCountryCode()
	 */
	public PhoneNumer setCountryCode(String value) {
		internalSetCountryCode(value);
		return this;
	}
	/** Internal setter for {@link #getCountryCode()} without chain call utility. */
	protected final void internalSetCountryCode(String value) {
		_listener.beforeSet(this, COUNTRY_CODE, value);
		_countryCode = value;
	}


	public final String getCountry() {
		return _country;
	}

	/**
	 * @see #getCountry()
	 */
	public PhoneNumer setCountry(String value) {
		internalSetCountry(value);
		return this;
	}
	/** Internal setter for {@link #getCountry()} without chain call utility. */
	protected final void internalSetCountry(String value) {
		_listener.beforeSet(this, COUNTRY, value);
		_country = value;
	}


	public final String getCityCode() {
		return _cityCode;
	}

	/**
	 * @see #getCityCode()
	 */
	public PhoneNumer setCityCode(String value) {
		internalSetCityCode(value);
		return this;
	}
	/** Internal setter for {@link #getCityCode()} without chain call utility. */
	protected final void internalSetCityCode(String value) {
		_listener.beforeSet(this, CITY_CODE, value);
		_cityCode = value;
	}


	/**
	 * Checks, whether {@link #getCityCode()} has a value.
	 */
	public final boolean hasCityCode() {
		return _cityCode != null;
	}

	public final String getCity() {
		return _city;
	}

	/**
	 * @see #getCity()
	 */
	public PhoneNumer setCity(String value) {
		internalSetCity(value);
		return this;
	}
	/** Internal setter for {@link #getCity()} without chain call utility. */
	protected final void internalSetCity(String value) {
		_listener.beforeSet(this, CITY, value);
		_city = value;
	}


	/**
	 * Checks, whether {@link #getCity()} has a value.
	 */
	public final boolean hasCity() {
		return _city != null;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public PhoneNumer registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public PhoneNumer unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return PHONE_NUMER__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			SHORTCUT, 
			PLUS, 
			ZERO_ZERO, 
			COUNTRY_CODE, 
			COUNTRY, 
			CITY_CODE, 
			CITY));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case SHORTCUT: return getShortcut();
			case PLUS: return getPlus();
			case ZERO_ZERO: return getZeroZero();
			case COUNTRY_CODE: return getCountryCode();
			case COUNTRY: return getCountry();
			case CITY_CODE: return getCityCode();
			case CITY: return getCity();
			default: return de.haumacher.msgbuf.observer.Observable.super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case SHORTCUT: setShortcut((String) value); break;
			case PLUS: setPlus((String) value); break;
			case ZERO_ZERO: setZeroZero((String) value); break;
			case COUNTRY_CODE: setCountryCode((String) value); break;
			case COUNTRY: setCountry((String) value); break;
			case CITY_CODE: setCityCode((String) value); break;
			case CITY: setCity((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static PhoneNumer readPhoneNumer(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		PhoneNumer result = new PhoneNumer();
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
		if (hasShortcut()) {
			out.name(SHORTCUT);
			out.value(getShortcut());
		}
		out.name(PLUS);
		out.value(getPlus());
		out.name(ZERO_ZERO);
		out.value(getZeroZero());
		out.name(COUNTRY_CODE);
		out.value(getCountryCode());
		out.name(COUNTRY);
		out.value(getCountry());
		if (hasCityCode()) {
			out.name(CITY_CODE);
			out.value(getCityCode());
		}
		if (hasCity()) {
			out.name(CITY);
			out.value(getCity());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case SHORTCUT: setShortcut(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PLUS: setPlus(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ZERO_ZERO: setZeroZero(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case COUNTRY_CODE: setCountryCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case COUNTRY: setCountry(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CITY_CODE: setCityCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CITY: setCity(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
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
		if (hasShortcut()) {
			out.name(SHORTCUT__ID);
			out.value(getShortcut());
		}
		out.name(PLUS__ID);
		out.value(getPlus());
		out.name(ZERO_ZERO__ID);
		out.value(getZeroZero());
		out.name(COUNTRY_CODE__ID);
		out.value(getCountryCode());
		out.name(COUNTRY__ID);
		out.value(getCountry());
		if (hasCityCode()) {
			out.name(CITY_CODE__ID);
			out.value(getCityCode());
		}
		if (hasCity()) {
			out.name(CITY__ID);
			out.value(getCity());
		}
	}

	/** Reads a new instance from the given reader. */
	public static PhoneNumer readPhoneNumer(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		PhoneNumer result = new PhoneNumer();
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
			case SHORTCUT__ID: setShortcut(in.nextString()); break;
			case PLUS__ID: setPlus(in.nextString()); break;
			case ZERO_ZERO__ID: setZeroZero(in.nextString()); break;
			case COUNTRY_CODE__ID: setCountryCode(in.nextString()); break;
			case COUNTRY__ID: setCountry(in.nextString()); break;
			case CITY_CODE__ID: setCityCode(in.nextString()); break;
			case CITY__ID: setCity(in.nextString()); break;
			default: in.skipValue(); 
		}
	}

}
