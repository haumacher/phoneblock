package de.haumacher.phoneblock.index.google;

/**
 * Message sent to the Google update service.
 */
public class UpdateMessage extends de.haumacher.msgbuf.data.AbstractDataObject {

	/**
	 * The type of an update.
	 */
	public enum Type implements de.haumacher.msgbuf.data.ProtocolEnum {

		/**
		 * The content of an URL was updated.
		 */
		URL_UPDATED("URL_UPDATED"),

		/**
		 * The resouce of an URL was deleted.
		 */
		URL_DELETED("URL_DELETED"),

		;

		private final String _protocolName;

		private Type(String protocolName) {
			_protocolName = protocolName;
		}

		/**
		 * The protocol name of a {@link Type} constant.
		 *
		 * @see #valueOfProtocol(String)
		 */
		@Override
		public String protocolName() {
			return _protocolName;
		}

		/** Looks up a {@link Type} constant by it's protocol name. */
		public static Type valueOfProtocol(String protocolName) {
			if (protocolName == null) { return null; }
			switch (protocolName) {
				case "URL_UPDATED": return URL_UPDATED;
				case "URL_DELETED": return URL_DELETED;
			}
			return URL_UPDATED;
		}

		/** Writes this instance to the given output. */
		public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
			out.value(protocolName());
		}

		/** Reads a new instance from the given reader. */
		public static Type readType(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
			return valueOfProtocol(in.nextString());
		}

		/** Writes this instance to the given binary output. */
		public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
			switch (this) {
				case URL_UPDATED: out.value(1); break;
				case URL_DELETED: out.value(2); break;
				default: out.value(0);
			}
		}

		/** Reads a new instance from the given binary reader. */
		public static Type readType(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
			switch (in.nextInt()) {
				case 1: return URL_UPDATED;
				case 2: return URL_DELETED;
				default: return URL_UPDATED;
			}
		}
	}

	/**
	 * Creates a {@link de.haumacher.phoneblock.index.google.UpdateMessage} instance.
	 */
	public static de.haumacher.phoneblock.index.google.UpdateMessage create() {
		return new de.haumacher.phoneblock.index.google.UpdateMessage();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.index.google.UpdateMessage} type in JSON format. */
	public static final String UPDATE_MESSAGE__TYPE = "UpdateMessage";

	/** @see #getUrl() */
	private static final String URL__PROP = "url";

	/** @see #getType() */
	private static final String TYPE__PROP = "type";

	private String _url = "";

	private de.haumacher.phoneblock.index.google.UpdateMessage.Type _type = de.haumacher.phoneblock.index.google.UpdateMessage.Type.URL_UPDATED;

	/**
	 * Creates a {@link UpdateMessage} instance.
	 *
	 * @see de.haumacher.phoneblock.index.google.UpdateMessage#create()
	 */
	protected UpdateMessage() {
		super();
	}

	/**
	 * The modified URL.
	 */
	public final String getUrl() {
		return _url;
	}

	/**
	 * @see #getUrl()
	 */
	public de.haumacher.phoneblock.index.google.UpdateMessage setUrl(String value) {
		internalSetUrl(value);
		return this;
	}

	/** Internal setter for {@link #getUrl()} without chain call utility. */
	protected final void internalSetUrl(String value) {
		_url = value;
	}

	/**
	 * The type of update to inform about.
	 */
	public final de.haumacher.phoneblock.index.google.UpdateMessage.Type getType() {
		return _type;
	}

	/**
	 * @see #getType()
	 */
	public de.haumacher.phoneblock.index.google.UpdateMessage setType(de.haumacher.phoneblock.index.google.UpdateMessage.Type value) {
		internalSetType(value);
		return this;
	}

	/** Internal setter for {@link #getType()} without chain call utility. */
	protected final void internalSetType(de.haumacher.phoneblock.index.google.UpdateMessage.Type value) {
		if (value == null) throw new IllegalArgumentException("Property 'type' cannot be null.");
		_type = value;
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.index.google.UpdateMessage readUpdateMessage(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.index.google.UpdateMessage result = new de.haumacher.phoneblock.index.google.UpdateMessage();
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
		out.name(URL__PROP);
		out.value(getUrl());
		out.name(TYPE__PROP);
		getType().writeTo(out);
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case URL__PROP: setUrl(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TYPE__PROP: setType(de.haumacher.phoneblock.index.google.UpdateMessage.Type.readType(in)); break;
			default: super.readField(in, field);
		}
	}

}
