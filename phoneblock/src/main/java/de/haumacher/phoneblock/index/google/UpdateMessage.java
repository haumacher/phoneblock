package de.haumacher.phoneblock.index.google;

/**
 * Message sent to the Google update service.
 */
public interface UpdateMessage extends de.haumacher.msgbuf.data.DataObject, de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

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
	static de.haumacher.phoneblock.index.google.UpdateMessage create() {
		return new de.haumacher.phoneblock.index.google.impl.UpdateMessage_Impl();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.index.google.UpdateMessage} type in JSON format. */
	String UPDATE_MESSAGE__TYPE = "UpdateMessage";

	/** @see #getUrl() */
	String URL__PROP = "url";

	/** @see #getType() */
	String TYPE__PROP = "type";

	/** Identifier for the property {@link #getUrl()} in binary format. */
	static final int URL__ID = 1;

	/** Identifier for the property {@link #getType()} in binary format. */
	static final int TYPE__ID = 2;

	/**
	 * The modified URL.
	 */
	String getUrl();

	/**
	 * @see #getUrl()
	 */
	de.haumacher.phoneblock.index.google.UpdateMessage setUrl(String value);

	/**
	 * The type of update to inform about.
	 */
	de.haumacher.phoneblock.index.google.UpdateMessage.Type getType();

	/**
	 * @see #getType()
	 */
	de.haumacher.phoneblock.index.google.UpdateMessage setType(de.haumacher.phoneblock.index.google.UpdateMessage.Type value);

	@Override
	public de.haumacher.phoneblock.index.google.UpdateMessage registerListener(de.haumacher.msgbuf.observer.Listener l);

	@Override
	public de.haumacher.phoneblock.index.google.UpdateMessage unregisterListener(de.haumacher.msgbuf.observer.Listener l);

	/** Reads a new instance from the given reader. */
	static de.haumacher.phoneblock.index.google.UpdateMessage readUpdateMessage(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.index.google.impl.UpdateMessage_Impl result = new de.haumacher.phoneblock.index.google.impl.UpdateMessage_Impl();
		result.readContent(in);
		return result;
	}

	/** Reads a new instance from the given reader. */
	static de.haumacher.phoneblock.index.google.UpdateMessage readUpdateMessage(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.index.google.UpdateMessage result = de.haumacher.phoneblock.index.google.impl.UpdateMessage_Impl.readUpdateMessage_Content(in);
		in.endObject();
		return result;
	}

}
