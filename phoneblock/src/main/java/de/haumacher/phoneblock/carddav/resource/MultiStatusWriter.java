/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.io.OutputStream;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.carddav.schema.DavSchema;

/**
 * Helpers to open and close the CardDAV {@code <d:multistatus>} envelope.
 * Centralizes the namespace bindings used everywhere in the response so that
 * resource render code can write elements without worrying about namespace
 * declarations.
 *
 * <p>
 * Predeclares: {@code DAV:} as default namespace, {@code carddav} as
 * {@code c}, and {@code calendarserver.org/ns/} as {@code s}.
 * </p>
 */
public final class MultiStatusWriter {

	private static final XMLOutputFactory FACTORY = createFactory();

	private static XMLOutputFactory createFactory() {
		XMLOutputFactory factory = XMLOutputFactory.newInstance();
		factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.FALSE);
		return factory;
	}

	private MultiStatusWriter() {
		// no instances
	}

	/**
	 * Opens a UTF-8 {@link XMLStreamWriter} on the given output stream, writes
	 * the XML declaration and the {@code <d:multistatus>} start element with
	 * predeclared namespace bindings.
	 */
	public static XMLStreamWriter open(OutputStream out) throws XMLStreamException {
		XMLStreamWriter writer = FACTORY.createXMLStreamWriter(out, "UTF-8");
		writer.writeStartDocument("UTF-8", "1.0");
		writer.setDefaultNamespace(DavSchema.DAV_NS);
		writer.setPrefix(CardDavSchema.CARDDAV_PREFIX, CardDavSchema.CARDDAV_NS);
		writer.setPrefix(CardDavSchema.CALENDARSERVER_PREFIX, CardDavSchema.CALENDARSERVER_NS);
		writer.writeStartElement(DavSchema.DAV_NS, "multistatus");
		writer.writeDefaultNamespace(DavSchema.DAV_NS);
		writer.writeNamespace(CardDavSchema.CARDDAV_PREFIX, CardDavSchema.CARDDAV_NS);
		writer.writeNamespace(CardDavSchema.CALENDARSERVER_PREFIX, CardDavSchema.CALENDARSERVER_NS);
		return writer;
	}

	/**
	 * Closes the multistatus element and the document, then flushes and closes
	 * the writer.
	 */
	public static void close(XMLStreamWriter writer) throws XMLStreamException {
		writer.writeEndElement();
		writer.writeEndDocument();
		writer.flush();
		writer.close();
	}
}
