package de.haumacher.phoneblock.app.api.model;

/**
 * Base message with common account settings fields.
 */
public abstract class AccountData extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/** Type codes for the {@link de.haumacher.phoneblock.app.api.model.AccountData} hierarchy. */
	public enum TypeKind {

		/** Type literal for {@link de.haumacher.phoneblock.app.api.model.UpdateAccountRequest}. */
		UPDATE_ACCOUNT_REQUEST,

		/** Type literal for {@link de.haumacher.phoneblock.app.api.model.AccountSettings}. */
		ACCOUNT_SETTINGS,
		;

	}

	/** Visitor interface for the {@link de.haumacher.phoneblock.app.api.model.AccountData} hierarchy.*/
	public interface Visitor<R,A,E extends Throwable> {

		/** Visit case for {@link de.haumacher.phoneblock.app.api.model.UpdateAccountRequest}.*/
		R visit(de.haumacher.phoneblock.app.api.model.UpdateAccountRequest self, A arg) throws E;

		/** Visit case for {@link de.haumacher.phoneblock.app.api.model.AccountSettings}.*/
		R visit(de.haumacher.phoneblock.app.api.model.AccountSettings self, A arg) throws E;

	}

	/** @see #getLang() */
	public static final String LANG__PROP = "lang";

	/** @see #getDialPrefix() */
	public static final String DIAL_PREFIX__PROP = "dialPrefix";

	/** @see #getDisplayName() */
	public static final String DISPLAY_NAME__PROP = "displayName";

	private String _lang = null;

	private String _dialPrefix = null;

	private String _displayName = null;

	/**
	 * Creates a {@link AccountData} instance.
	 */
	protected AccountData() {
		super();
	}

	/** The type code of this instance. */
	public abstract TypeKind kind();

	/**
	 * The preferred language tag (e.g., "de", "en-US", "pt-BR").
	 */
	public final String getLang() {
		return _lang;
	}

	/**
	 * @see #getLang()
	 */
	public de.haumacher.phoneblock.app.api.model.AccountData setLang(String value) {
		internalSetLang(value);
		return this;
	}

	/** Internal setter for {@link #getLang()} without chain call utility. */
	protected final void internalSetLang(String value) {
		_listener.beforeSet(this, LANG__PROP, value);
		_lang = value;
	}

	/**
	 * Checks, whether {@link #getLang()} has a value.
	 */
	public final boolean hasLang() {
		return _lang != null;
	}

	/**
	 * The user's country dial prefix (e.g., "+49", "+1", "+351").
	 */
	public final String getDialPrefix() {
		return _dialPrefix;
	}

	/**
	 * @see #getDialPrefix()
	 */
	public de.haumacher.phoneblock.app.api.model.AccountData setDialPrefix(String value) {
		internalSetDialPrefix(value);
		return this;
	}

	/** Internal setter for {@link #getDialPrefix()} without chain call utility. */
	protected final void internalSetDialPrefix(String value) {
		_listener.beforeSet(this, DIAL_PREFIX__PROP, value);
		_dialPrefix = value;
	}

	/**
	 * Checks, whether {@link #getDialPrefix()} has a value.
	 */
	public final boolean hasDialPrefix() {
		return _dialPrefix != null;
	}

	/**
	 * The user's display name.
	 */
	public final String getDisplayName() {
		return _displayName;
	}

	/**
	 * @see #getDisplayName()
	 */
	public de.haumacher.phoneblock.app.api.model.AccountData setDisplayName(String value) {
		internalSetDisplayName(value);
		return this;
	}

	/** Internal setter for {@link #getDisplayName()} without chain call utility. */
	protected final void internalSetDisplayName(String value) {
		_listener.beforeSet(this, DISPLAY_NAME__PROP, value);
		_displayName = value;
	}

	/**
	 * Checks, whether {@link #getDisplayName()} has a value.
	 */
	public final boolean hasDisplayName() {
		return _displayName != null;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.app.api.model.AccountData registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.app.api.model.AccountData unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			LANG__PROP, 
			DIAL_PREFIX__PROP, 
			DISPLAY_NAME__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case LANG__PROP: return getLang();
			case DIAL_PREFIX__PROP: return getDialPrefix();
			case DISPLAY_NAME__PROP: return getDisplayName();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case LANG__PROP: internalSetLang((String) value); break;
			case DIAL_PREFIX__PROP: internalSetDialPrefix((String) value); break;
			case DISPLAY_NAME__PROP: internalSetDisplayName((String) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.AccountData readAccountData(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.AccountData result;
		in.beginArray();
		String type = in.nextString();
		switch (type) {
			case UpdateAccountRequest.UPDATE_ACCOUNT_REQUEST__TYPE: result = de.haumacher.phoneblock.app.api.model.UpdateAccountRequest.readUpdateAccountRequest(in); break;
			case AccountSettings.ACCOUNT_SETTINGS__TYPE: result = de.haumacher.phoneblock.app.api.model.AccountSettings.readAccountSettings(in); break;
			default: in.skipValue(); result = null; break;
		}
		in.endArray();
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		out.beginArray();
		out.value(jsonType());
		writeContent(out);
		out.endArray();
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		if (hasLang()) {
			out.name(LANG__PROP);
			out.value(getLang());
		}
		if (hasDialPrefix()) {
			out.name(DIAL_PREFIX__PROP);
			out.value(getDialPrefix());
		}
		if (hasDisplayName()) {
			out.name(DISPLAY_NAME__PROP);
			out.value(getDisplayName());
		}
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case LANG__PROP: setLang(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DIAL_PREFIX__PROP: setDialPrefix(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DISPLAY_NAME__PROP: setDisplayName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.AccountData} type. */
	public static final String ACCOUNT_DATA__XML_ELEMENT = "account-data";

	/** XML attribute or element name of a {@link #getLang} property. */
	private static final String LANG__XML_ATTR = "lang";

	/** XML attribute or element name of a {@link #getDialPrefix} property. */
	private static final String DIAL_PREFIX__XML_ATTR = "dial-prefix";

	/** XML attribute or element name of a {@link #getDisplayName} property. */
	private static final String DISPLAY_NAME__XML_ATTR = "display-name";

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(LANG__XML_ATTR, getLang());
		out.writeAttribute(DIAL_PREFIX__XML_ATTR, getDialPrefix());
		out.writeAttribute(DISPLAY_NAME__XML_ATTR, getDisplayName());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		// No element fields.
	}

	/** Creates a new {@link de.haumacher.phoneblock.app.api.model.AccountData} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AccountData readAccountData_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		switch (in.getLocalName()) {
			case UpdateAccountRequest.UPDATE_ACCOUNT_REQUEST__XML_ELEMENT: {
				return de.haumacher.phoneblock.app.api.model.UpdateAccountRequest.readUpdateAccountRequest_XmlContent(in);
			}

			case AccountSettings.ACCOUNT_SETTINGS__XML_ELEMENT: {
				return de.haumacher.phoneblock.app.api.model.AccountSettings.readAccountSettings_XmlContent(in);
			}

			default: {
				internalSkipUntilMatchingEndElement(in);
				return null;
			}
		}
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
			case LANG__XML_ATTR: {
				setLang(value);
				break;
			}
			case DIAL_PREFIX__XML_ATTR: {
				setDialPrefix(value);
				break;
			}
			case DISPLAY_NAME__XML_ATTR: {
				setDisplayName(value);
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
			case LANG__XML_ATTR: {
				setLang(in.getElementText());
				break;
			}
			case DIAL_PREFIX__XML_ATTR: {
				setDialPrefix(in.getElementText());
				break;
			}
			case DISPLAY_NAME__XML_ATTR: {
				setDisplayName(in.getElementText());
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

	/** Creates a new {@link AccountData} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static AccountData readAccountData(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.app.api.model.AccountData.readAccountData_XmlContent(in);
	}

	/** Accepts the given visitor. */
	public abstract <R,A,E extends Throwable> R visit(Visitor<R,A,E> v, A arg) throws E;

}
