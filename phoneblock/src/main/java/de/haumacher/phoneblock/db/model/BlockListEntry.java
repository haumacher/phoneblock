package de.haumacher.phoneblock.db.model;

public class BlockListEntry extends PhoneSummary {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.model.BlockListEntry} instance.
	 */
	public static de.haumacher.phoneblock.db.model.BlockListEntry create() {
		return new de.haumacher.phoneblock.db.model.BlockListEntry();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.model.BlockListEntry} type in JSON format. */
	public static final String BLOCK_LIST_ENTRY__TYPE = "BlockListEntry";

	/**
	 * Creates a {@link BlockListEntry} instance.
	 *
	 * @see de.haumacher.phoneblock.db.model.BlockListEntry#create()
	 */
	protected BlockListEntry() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.BLOCK_LIST_ENTRY;
	}

	@Override
	public de.haumacher.phoneblock.db.model.BlockListEntry setPhone(String value) {
		internalSetPhone(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.BlockListEntry setVotes(int value) {
		internalSetVotes(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.model.BlockListEntry setRating(de.haumacher.phoneblock.db.model.Rating value) {
		internalSetRating(value);
		return this;
	}

	@Override
	public String jsonType() {
		return BLOCK_LIST_ENTRY__TYPE;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.model.BlockListEntry readBlockListEntry(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.BlockListEntry result = new de.haumacher.phoneblock.db.model.BlockListEntry();
		result.readContent(in);
		return result;
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.db.model.BlockListEntry} type. */
	public static final String BLOCK_LIST_ENTRY__XML_ELEMENT = "block-list-entry";

	@Override
	public String getXmlTagName() {
		return BLOCK_LIST_ENTRY__XML_ELEMENT;
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

	/** Creates a new {@link de.haumacher.phoneblock.db.model.BlockListEntry} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static BlockListEntry readBlockListEntry_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		BlockListEntry result = new BlockListEntry();
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

	/** Creates a new {@link BlockListEntry} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static BlockListEntry readBlockListEntry(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.db.model.BlockListEntry.readBlockListEntry_XmlContent(in);
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.db.model.PhoneSummary.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
