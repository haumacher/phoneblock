package de.haumacher.phoneblock.analysis.model;

public interface NationalDestinationCode extends de.haumacher.msgbuf.data.DataObject, de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.analysis.model.NationalDestinationCode} instance.
	 */
	static de.haumacher.phoneblock.analysis.model.NationalDestinationCode create() {
		return new de.haumacher.phoneblock.analysis.model.impl.NationalDestinationCode_Impl();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.analysis.model.NationalDestinationCode} type in JSON format. */
	String NATIONAL_DESTINATION_CODE__TYPE = "NationalDestinationCode";

	/** @see #getPrefix() */
	String PREFIX__PROP = "prefix";

	/** @see #getMaxDigits() */
	String MAX_DIGITS__PROP = "maxDigits";

	/** @see #getMinDigits() */
	String MIN_DIGITS__PROP = "minDigits";

	/** @see #getUsage() */
	String USAGE__PROP = "usage";

	/** @see #getInfo() */
	String INFO__PROP = "info";

	/** Identifier for the property {@link #getPrefix()} in binary format. */
	static final int PREFIX__ID = 1;

	/** Identifier for the property {@link #getMaxDigits()} in binary format. */
	static final int MAX_DIGITS__ID = 2;

	/** Identifier for the property {@link #getMinDigits()} in binary format. */
	static final int MIN_DIGITS__ID = 3;

	/** Identifier for the property {@link #getUsage()} in binary format. */
	static final int USAGE__ID = 4;

	/** Identifier for the property {@link #getInfo()} in binary format. */
	static final int INFO__ID = 5;

	String getPrefix();

	/**
	 * @see #getPrefix()
	 */
	de.haumacher.phoneblock.analysis.model.NationalDestinationCode setPrefix(String value);

	int getMaxDigits();

	/**
	 * @see #getMaxDigits()
	 */
	de.haumacher.phoneblock.analysis.model.NationalDestinationCode setMaxDigits(int value);

	int getMinDigits();

	/**
	 * @see #getMinDigits()
	 */
	de.haumacher.phoneblock.analysis.model.NationalDestinationCode setMinDigits(int value);

	String getUsage();

	/**
	 * @see #getUsage()
	 */
	de.haumacher.phoneblock.analysis.model.NationalDestinationCode setUsage(String value);

	String getInfo();

	/**
	 * @see #getInfo()
	 */
	de.haumacher.phoneblock.analysis.model.NationalDestinationCode setInfo(String value);

	@Override
	public de.haumacher.phoneblock.analysis.model.NationalDestinationCode registerListener(de.haumacher.msgbuf.observer.Listener l);

	@Override
	public de.haumacher.phoneblock.analysis.model.NationalDestinationCode unregisterListener(de.haumacher.msgbuf.observer.Listener l);

	/** Reads a new instance from the given reader. */
	static de.haumacher.phoneblock.analysis.model.NationalDestinationCode readNationalDestinationCode(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.analysis.model.impl.NationalDestinationCode_Impl result = new de.haumacher.phoneblock.analysis.model.impl.NationalDestinationCode_Impl();
		result.readContent(in);
		return result;
	}

	/** Reads a new instance from the given reader. */
	static de.haumacher.phoneblock.analysis.model.NationalDestinationCode readNationalDestinationCode(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.analysis.model.NationalDestinationCode result = de.haumacher.phoneblock.analysis.model.impl.NationalDestinationCode_Impl.readNationalDestinationCode_Content(in);
		in.endObject();
		return result;
	}

	/** Creates a new {@link NationalDestinationCode} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static NationalDestinationCode readNationalDestinationCode(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.analysis.model.impl.NationalDestinationCode_Impl.readNationalDestinationCode_XmlContent(in);
	}

}
