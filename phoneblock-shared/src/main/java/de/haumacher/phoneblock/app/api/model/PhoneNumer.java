package de.haumacher.phoneblock.app.api.model;

public class PhoneNumer extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.PhoneNumer} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.PhoneNumer create() {
		return new de.haumacher.phoneblock.app.api.model.PhoneNumer();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.PhoneNumer} type in JSON format. */
	public static final String PHONE_NUMER__TYPE = "PhoneNumer";

	/** @see #getId() */
	private static final String ID__PROP = "id";

	/** @see #getShortcut() */
	private static final String SHORTCUT__PROP = "shortcut";

	/** @see #getPlus() */
	private static final String PLUS__PROP = "plus";

	/** @see #getZeroZero() */
	private static final String ZERO_ZERO__PROP = "zeroZero";

	/** @see #getDial() */
	private static final String DIAL__PROP = "dial";

	/** @see #getCountryCode() */
	private static final String COUNTRY_CODE__PROP = "countryCode";

	/** @see #getCountry() */
	private static final String COUNTRY__PROP = "country";

	/** @see #getCityCode() */
	private static final String CITY_CODE__PROP = "cityCode";

	/** @see #getCity() */
	private static final String CITY__PROP = "city";

	/** @see #getUsage() */
	private static final String USAGE__PROP = "usage";

	private String _id = "";

	private String _shortcut = "";

	private String _plus = "";

	private String _zeroZero = "";

	private String _dial = "";

	private String _countryCode = "";

	private String _country = "";

	private String _cityCode = null;

	private String _city = null;

	private String _usage = null;

	/**
	 * Creates a {@link PhoneNumer} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.PhoneNumer#create()
	 */
	protected PhoneNumer() {
		super();
	}

	/**
	 * The representation stored in the database.
	 */
	public final String getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneNumer setId(String value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(String value) {
		_id = value;
	}

	/**
	 * The local number with country ID prefx.
	 */
	public final String getShortcut() {
		return _shortcut;
	}

	/**
	 * @see #getShortcut()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneNumer setShortcut(String value) {
		internalSetShortcut(value);
		return this;
	}

	/** Internal setter for {@link #getShortcut()} without chain call utility. */
	protected final void internalSetShortcut(String value) {
		_shortcut = value;
	}

	public final String getPlus() {
		return _plus;
	}

	/**
	 * @see #getPlus()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneNumer setPlus(String value) {
		internalSetPlus(value);
		return this;
	}

	/** Internal setter for {@link #getPlus()} without chain call utility. */
	protected final void internalSetPlus(String value) {
		_plus = value;
	}

	/**
	 * International format with "00" prefix (legacy, for backward compatibility and DB operations).
	 */
	public final String getZeroZero() {
		return _zeroZero;
	}

	/**
	 * @see #getZeroZero()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneNumer setZeroZero(String value) {
		internalSetZeroZero(value);
		return this;
	}

	/** Internal setter for {@link #getZeroZero()} without chain call utility. */
	protected final void internalSetZeroZero(String value) {
		_zeroZero = value;
	}

	/**
	 * International format with the user's country-specific international prefix. This is the format a user would actually dial from their country (e.g., "011" for USA, "00" for most European countries, "810" for Russia).
	 */
	public final String getDial() {
		return _dial;
	}

	/**
	 * @see #getDial()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneNumer setDial(String value) {
		internalSetDial(value);
		return this;
	}

	/** Internal setter for {@link #getDial()} without chain call utility. */
	protected final void internalSetDial(String value) {
		_dial = value;
	}

	public final String getCountryCode() {
		return _countryCode;
	}

	/**
	 * @see #getCountryCode()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneNumer setCountryCode(String value) {
		internalSetCountryCode(value);
		return this;
	}

	/** Internal setter for {@link #getCountryCode()} without chain call utility. */
	protected final void internalSetCountryCode(String value) {
		_countryCode = value;
	}

	public final String getCountry() {
		return _country;
	}

	/**
	 * @see #getCountry()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneNumer setCountry(String value) {
		internalSetCountry(value);
		return this;
	}

	/** Internal setter for {@link #getCountry()} without chain call utility. */
	protected final void internalSetCountry(String value) {
		_country = value;
	}

	public final String getCityCode() {
		return _cityCode;
	}

	/**
	 * @see #getCityCode()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneNumer setCityCode(String value) {
		internalSetCityCode(value);
		return this;
	}

	/** Internal setter for {@link #getCityCode()} without chain call utility. */
	protected final void internalSetCityCode(String value) {
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
	public de.haumacher.phoneblock.app.api.model.PhoneNumer setCity(String value) {
		internalSetCity(value);
		return this;
	}

	/** Internal setter for {@link #getCity()} without chain call utility. */
	protected final void internalSetCity(String value) {
		_city = value;
	}

	/**
	 * Checks, whether {@link #getCity()} has a value.
	 */
	public final boolean hasCity() {
		return _city != null;
	}

	/**
	 * The kind of number
	 */
	public final String getUsage() {
		return _usage;
	}

	/**
	 * @see #getUsage()
	 */
	public de.haumacher.phoneblock.app.api.model.PhoneNumer setUsage(String value) {
		internalSetUsage(value);
		return this;
	}

	/** Internal setter for {@link #getUsage()} without chain call utility. */
	protected final void internalSetUsage(String value) {
		_usage = value;
	}

	/**
	 * Checks, whether {@link #getUsage()} has a value.
	 */
	public final boolean hasUsage() {
		return _usage != null;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.PhoneNumer readPhoneNumer(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.PhoneNumer result = new de.haumacher.phoneblock.app.api.model.PhoneNumer();
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
		out.name(SHORTCUT__PROP);
		out.value(getShortcut());
		out.name(PLUS__PROP);
		out.value(getPlus());
		out.name(ZERO_ZERO__PROP);
		out.value(getZeroZero());
		out.name(DIAL__PROP);
		out.value(getDial());
		out.name(COUNTRY_CODE__PROP);
		out.value(getCountryCode());
		out.name(COUNTRY__PROP);
		out.value(getCountry());
		if (hasCityCode()) {
			out.name(CITY_CODE__PROP);
			out.value(getCityCode());
		}
		if (hasCity()) {
			out.name(CITY__PROP);
			out.value(getCity());
		}
		if (hasUsage()) {
			out.name(USAGE__PROP);
			out.value(getUsage());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case SHORTCUT__PROP: setShortcut(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case PLUS__PROP: setPlus(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case ZERO_ZERO__PROP: setZeroZero(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DIAL__PROP: setDial(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case COUNTRY_CODE__PROP: setCountryCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case COUNTRY__PROP: setCountry(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CITY_CODE__PROP: setCityCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case CITY__PROP: setCity(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case USAGE__PROP: setUsage(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.PhoneNumer} type. */
	public static final String PHONE_NUMER__XML_ELEMENT = "phone-numer";

	/** XML attribute or element name of a {@link #getId} property. */
	private static final String ID__XML_ATTR = "id";

	/** XML attribute or element name of a {@link #getShortcut} property. */
	private static final String SHORTCUT__XML_ATTR = "shortcut";

	/** XML attribute or element name of a {@link #getPlus} property. */
	private static final String PLUS__XML_ATTR = "plus";

	/** XML attribute or element name of a {@link #getZeroZero} property. */
	private static final String ZERO_ZERO__XML_ATTR = "zero-zero";

	/** XML attribute or element name of a {@link #getDial} property. */
	private static final String DIAL__XML_ATTR = "dial";

	/** XML attribute or element name of a {@link #getCountryCode} property. */
	private static final String COUNTRY_CODE__XML_ATTR = "country-code";

	/** XML attribute or element name of a {@link #getCountry} property. */
	private static final String COUNTRY__XML_ATTR = "country";

	/** XML attribute or element name of a {@link #getCityCode} property. */
	private static final String CITY_CODE__XML_ATTR = "city-code";

	/** XML attribute or element name of a {@link #getCity} property. */
	private static final String CITY__XML_ATTR = "city";

	/** XML attribute or element name of a {@link #getUsage} property. */
	private static final String USAGE__XML_ATTR = "usage";

	@Override
	public String getXmlTagName() {
		return PHONE_NUMER__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(ID__XML_ATTR, getId());
		out.writeAttribute(SHORTCUT__XML_ATTR, getShortcut());
		out.writeAttribute(PLUS__XML_ATTR, getPlus());
		out.writeAttribute(ZERO_ZERO__XML_ATTR, getZeroZero());
		out.writeAttribute(DIAL__XML_ATTR, getDial());
		out.writeAttribute(COUNTRY_CODE__XML_ATTR, getCountryCode());
		out.writeAttribute(COUNTRY__XML_ATTR, getCountry());
		out.writeAttribute(CITY_CODE__XML_ATTR, getCityCode());
		out.writeAttribute(CITY__XML_ATTR, getCity());
		out.writeAttribute(USAGE__XML_ATTR, getUsage());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.PhoneNumer} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static PhoneNumer readPhoneNumer_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		PhoneNumer result = new PhoneNumer();
		result.readContentXml(in);
		return result;
	}

	/** Reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	protected final void readContentXml(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		for (int n = 0, cnt = in.getAttributeCount(); n < cnt; n++) {
			String name = in.getAttributeLocalName(n);
			String value = in.getAttributeValue(n);

			readFieldXmlAttribute(name, value);
		}
		while (true) {
			int event = in.nextTag();
			if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT) {
				break;
			}
			assert event == javax.xml.stream.XMLStreamConstants.START_ELEMENT;

			String localName = in.getLocalName();
			readFieldXmlElement(in, localName);
		}
	}

	/** Parses the given attribute value and assigns it to the field with the given name. */
	protected void readFieldXmlAttribute(String name, String value) {
		switch (name) {
			case ID__XML_ATTR: {
				setId(value);
				break;
			}
			case SHORTCUT__XML_ATTR: {
				setShortcut(value);
				break;
			}
			case PLUS__XML_ATTR: {
				setPlus(value);
				break;
			}
			case ZERO_ZERO__XML_ATTR: {
				setZeroZero(value);
				break;
			}
			case DIAL__XML_ATTR: {
				setDial(value);
				break;
			}
			case COUNTRY_CODE__XML_ATTR: {
				setCountryCode(value);
				break;
			}
			case COUNTRY__XML_ATTR: {
				setCountry(value);
				break;
			}
			case CITY_CODE__XML_ATTR: {
				setCityCode(value);
				break;
			}
			case CITY__XML_ATTR: {
				setCity(value);
				break;
			}
			case USAGE__XML_ATTR: {
				setUsage(value);
				break;
			}
			default: {
				// Skip unknown attribute.
			}
		}
	}

	/** Reads the element under the cursor and assigns its contents to the field with the given name. */
	protected void readFieldXmlElement(javax.xml.stream.XMLStreamReader in, String localName) throws javax.xml.stream.XMLStreamException {
		switch (localName) {
			case ID__XML_ATTR: {
				setId(in.getElementText());
				break;
			}
			case SHORTCUT__XML_ATTR: {
				setShortcut(in.getElementText());
				break;
			}
			case PLUS__XML_ATTR: {
				setPlus(in.getElementText());
				break;
			}
			case ZERO_ZERO__XML_ATTR: {
				setZeroZero(in.getElementText());
				break;
			}
			case DIAL__XML_ATTR: {
				setDial(in.getElementText());
				break;
			}
			case COUNTRY_CODE__XML_ATTR: {
				setCountryCode(in.getElementText());
				break;
			}
			case COUNTRY__XML_ATTR: {
				setCountry(in.getElementText());
				break;
			}
			case CITY_CODE__XML_ATTR: {
				setCityCode(in.getElementText());
				break;
			}
			case CITY__XML_ATTR: {
				setCity(in.getElementText());
				break;
			}
			case USAGE__XML_ATTR: {
				setUsage(in.getElementText());
				break;
			}
			default: {
				internalSkipUntilMatchingEndElement(in);
			}
		}
	}

	protected static final void internalSkipUntilMatchingEndElement(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		int level = 0;
		while (true) {
			switch (in.next()) {
				case javax.xml.stream.XMLStreamConstants.START_ELEMENT: level++; break;
				case javax.xml.stream.XMLStreamConstants.END_ELEMENT: if (level == 0) { return; } else { level--; break; }
			}
		}
	}

	/** Creates a new {@link PhoneNumer} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static PhoneNumer readPhoneNumer(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.PhoneNumer.readPhoneNumer_XmlContent(in);
	}

}
