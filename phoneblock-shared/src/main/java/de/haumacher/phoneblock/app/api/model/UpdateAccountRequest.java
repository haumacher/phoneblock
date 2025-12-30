package de.haumacher.phoneblock.app.api.model;

/**
 * Request to update user account settings.
 */
public class UpdateAccountRequest extends AccountData {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.UpdateAccountRequest} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.UpdateAccountRequest create() {
		return new de.haumacher.phoneblock.app.api.model.UpdateAccountRequest();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.UpdateAccountRequest} type in JSON format. */
	public static final String UPDATE_ACCOUNT_REQUEST__TYPE = "UpdateAccountRequest";

	/** @see #getCountryCode() */
	public static final String COUNTRY_CODE__PROP = "countryCode";

	private String _countryCode = null;

	/**
	 * Creates a {@link UpdateAccountRequest} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.UpdateAccountRequest#create()
	 */
	protected UpdateAccountRequest() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.UPDATE_ACCOUNT_REQUEST;
	}

	/**
	 * ISO 3166-1 alpha-2 country code (e.g., "DE", "US", "BR"). If provided, the server will convert it to the corresponding dial prefix.
	 */
	public final String getCountryCode() {
		return _countryCode;
	}

	/**
	 * @see #getCountryCode()
	 */
	public de.haumacher.phoneblock.app.api.model.UpdateAccountRequest setCountryCode(String value) {
		internalSetCountryCode(value);
		return this;
	}

	/** Internal setter for {@link #getCountryCode()} without chain call utility. */
	protected final void internalSetCountryCode(String value) {
		_listener.beforeSet(this, COUNTRY_CODE__PROP, value);
		_countryCode = value;
	}

	/**
	 * Checks, whether {@link #getCountryCode()} has a value.
	 */
	public final boolean hasCountryCode() {
		return _countryCode != null;
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.UpdateAccountRequest setLang(String value) {
		internalSetLang(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.UpdateAccountRequest setDialPrefix(String value) {
		internalSetDialPrefix(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.UpdateAccountRequest setDisplayName(String value) {
		internalSetDisplayName(value);
		return this;
	}

	@Override
	public String jsonType() {
		return UPDATE_ACCOUNT_REQUEST__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			COUNTRY_CODE__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case COUNTRY_CODE__PROP: return getCountryCode();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case COUNTRY_CODE__PROP: internalSetCountryCode((String) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.UpdateAccountRequest readUpdateAccountRequest(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.UpdateAccountRequest result = new de.haumacher.phoneblock.app.api.model.UpdateAccountRequest();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		if (hasCountryCode()) {
			out.name(COUNTRY_CODE__PROP);
			out.value(getCountryCode());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case COUNTRY_CODE__PROP: setCountryCode(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.UpdateAccountRequest} type. */
	public static final String UPDATE_ACCOUNT_REQUEST__XML_ELEMENT = "update-account-request";

	/** XML attribute or element name of a {@link #getCountryCode} property. */
	private static final String COUNTRY_CODE__XML_ATTR = "country-code";

	@Override
	public String getXmlTagName() {
		return UPDATE_ACCOUNT_REQUEST__XML_ELEMENT;
	}

	/** Serializes all fields that are written as XML attributes. */
	@Override
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeAttributes(out);
		out.writeAttribute(COUNTRY_CODE__XML_ATTR, getCountryCode());
	}

	/** Serializes all fields that are written as XML elements. */
	@Override
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeElements(out);
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.UpdateAccountRequest} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static UpdateAccountRequest readUpdateAccountRequest_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		UpdateAccountRequest result = new UpdateAccountRequest();
		result.readContentXml(in);
		return result;
	}

	@Override
	protected void readFieldXmlAttribute(String name, String value) {
		switch (name) {
			case COUNTRY_CODE__XML_ATTR: {
				setCountryCode(value);
				break;
			}
			default: {
				super.readFieldXmlAttribute(name, value);
			}
		}
	}

	@Override
	protected void readFieldXmlElement(javax.xml.stream.XMLStreamReader in, String localName) throws javax.xml.stream.XMLStreamException {
		switch (localName) {
			case COUNTRY_CODE__XML_ATTR: {
				setCountryCode(in.getElementText());
				break;
			}
			default: {
				super.readFieldXmlElement(in, localName);
			}
		}
	}

	/** Creates a new {@link UpdateAccountRequest} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static UpdateAccountRequest readUpdateAccountRequest(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.UpdateAccountRequest.readUpdateAccountRequest_XmlContent(in);
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.app.api.model.AccountData.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
