package de.haumacher.phoneblock.app.api.model;

/**
 * Response from account settings operations.
 */
public class AccountSettings extends AccountData {

	/**
	 * Creates a {@link de.haumacher.phoneblock.app.api.model.AccountSettings} instance.
	 */
	public static de.haumacher.phoneblock.app.api.model.AccountSettings create() {
		return new de.haumacher.phoneblock.app.api.model.AccountSettings();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.app.api.model.AccountSettings} type in JSON format. */
	public static final String ACCOUNT_SETTINGS__TYPE = "AccountSettings";

	/** @see #getEmail() */
	public static final String EMAIL__PROP = "email";

	private String _email = null;

	/**
	 * Creates a {@link AccountSettings} instance.
	 *
	 * @see de.haumacher.phoneblock.app.api.model.AccountSettings#create()
	 */
	protected AccountSettings() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.ACCOUNT_SETTINGS;
	}

	/**
	 * The user's email address.
	 */
	public final String getEmail() {
		return _email;
	}

	/**
	 * @see #getEmail()
	 */
	public de.haumacher.phoneblock.app.api.model.AccountSettings setEmail(String value) {
		internalSetEmail(value);
		return this;
	}

	/** Internal setter for {@link #getEmail()} without chain call utility. */
	protected final void internalSetEmail(String value) {
		_listener.beforeSet(this, EMAIL__PROP, value);
		_email = value;
	}

	/**
	 * Checks, whether {@link #getEmail()} has a value.
	 */
	public final boolean hasEmail() {
		return _email != null;
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.AccountSettings setLang(String value) {
		internalSetLang(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.AccountSettings setDialPrefix(String value) {
		internalSetDialPrefix(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.AccountSettings setDisplayName(String value) {
		internalSetDisplayName(value);
		return this;
	}

	@Override
	public String jsonType() {
		return ACCOUNT_SETTINGS__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			EMAIL__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case EMAIL__PROP: return getEmail();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case EMAIL__PROP: internalSetEmail((String) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.AccountSettings readAccountSettings(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.AccountSettings result = new de.haumacher.phoneblock.app.api.model.AccountSettings();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		if (hasEmail()) {
			out.name(EMAIL__PROP);
			out.value(getEmail());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case EMAIL__PROP: setEmail(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.AccountSettings} type. */
	public static final String ACCOUNT_SETTINGS__XML_ELEMENT = "account-settings";

	/** XML attribute or element name of a {@link #getEmail} property. */
	private static final String EMAIL__XML_ATTR = "email";

	@Override
	public String getXmlTagName() {
		return ACCOUNT_SETTINGS__XML_ELEMENT;
	}

	/** Serializes all fields that are written as XML attributes. */
	@Override
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeAttributes(out);
		out.writeAttribute(EMAIL__XML_ATTR, getEmail());
	}

	/** Serializes all fields that are written as XML elements. */
	@Override
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeElements(out);
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.AccountSettings} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AccountSettings readAccountSettings_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		AccountSettings result = new AccountSettings();
		result.readContentXml(in);
		return result;
	}

	@Override
	protected void readFieldXmlAttribute(String name, String value) {
		switch (name) {
			case EMAIL__XML_ATTR: {
				setEmail(value);
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
			case EMAIL__XML_ATTR: {
				setEmail(in.getElementText());
				break;
			}
			default: {
				super.readFieldXmlElement(in, localName);
			}
		}
	}

	/** Creates a new {@link AccountSettings} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AccountSettings readAccountSettings(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.AccountSettings.readAccountSettings_XmlContent(in);
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.app.api.model.AccountData.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
