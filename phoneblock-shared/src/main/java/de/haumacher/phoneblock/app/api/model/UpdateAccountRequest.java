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

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.app.api.model.UpdateAccountRequest readUpdateAccountRequest(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.app.api.model.UpdateAccountRequest result = new de.haumacher.phoneblock.app.api.model.UpdateAccountRequest();
		result.readContent(in);
		return result;
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.app.api.model.UpdateAccountRequest} type. */
	public static final String UPDATE_ACCOUNT_REQUEST__XML_ELEMENT = "update-account-request";

	@Override
	public String getXmlTagName() {
		return UPDATE_ACCOUNT_REQUEST__XML_ELEMENT;
	}

	/** Serializes all fields that are written as XML attributes. */
	@Override
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		super.writeAttributes(out);
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
			default: {
				super.readFieldXmlAttribute(name, value);
			}
		}
	}

	@Override
	protected void readFieldXmlElement(javax.xml.stream.XMLStreamReader in, String localName) throws javax.xml.stream.XMLStreamException {
		switch (localName) {
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
