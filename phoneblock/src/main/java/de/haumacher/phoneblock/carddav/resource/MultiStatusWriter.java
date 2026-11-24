/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.carddav.schema.DavSchema;

/**
 * Output of a CardDAV {@code <d:multistatus>} response.
 *
 * <p>
 * Combines an {@link XMLStreamWriter} for content rendered per request with the
 * ability to splice pre-rendered fragments ({@link #writeRaw(byte[])}) into the
 * same stream. The envelope is written as fixed bytes, so that the stream writer
 * never has an open start tag at the level of the {@code <d:response>} elements
 * and raw fragments can be placed between any two complete elements.
 * </p>
 *
 * <p>
 * Namespace bindings: {@code DAV:} is the default namespace, {@code carddav} is
 * bound to {@code c} and {@code calendarserver.org/ns/} to {@code s}. Fragments
 * rendered with {@link #openFragment(OutputStream)} use the same bindings without
 * declaring them, so they are valid only inside this envelope.
 * </p>
 */
public final class MultiStatusWriter {

	private static final XMLOutputFactory FACTORY = createFactory();

	private static final byte[] HEADER = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
		+ "<multistatus xmlns=\"" + DavSchema.DAV_NS + "\""
		+ " xmlns:" + CardDavSchema.CARDDAV_PREFIX + "=\"" + CardDavSchema.CARDDAV_NS + "\""
		+ " xmlns:" + CardDavSchema.CALENDARSERVER_PREFIX + "=\"" + CardDavSchema.CALENDARSERVER_NS + "\">")
		.getBytes(StandardCharsets.UTF_8);

	private static final byte[] FOOTER = "</multistatus>".getBytes(StandardCharsets.UTF_8);

	private final OutputStream _out;

	private final XMLStreamWriter _xml;

	private static XMLOutputFactory createFactory() {
		XMLOutputFactory factory = XMLOutputFactory.newInstance();
		factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.FALSE);
		return factory;
	}

	private MultiStatusWriter(OutputStream out, XMLStreamWriter xml) {
		_out = out;
		_xml = xml;
	}

	/**
	 * Writes the XML declaration and the {@code <d:multistatus>} start tag to the
	 * given stream and returns a writer for the responses.
	 */
	public static MultiStatusWriter open(OutputStream out) throws XMLStreamException, IOException {
		out.write(HEADER);
		return new MultiStatusWriter(out, openFragment(out));
	}

	/**
	 * Creates an {@link XMLStreamWriter} for content inside the multistatus
	 * envelope, using the envelope's namespace bindings without declaring them.
	 */
	static XMLStreamWriter openFragment(OutputStream out) throws XMLStreamException {
		XMLStreamWriter writer = FACTORY.createXMLStreamWriter(out, "UTF-8");
		writer.setDefaultNamespace(DavSchema.DAV_NS);
		writer.setPrefix(CardDavSchema.CARDDAV_PREFIX, CardDavSchema.CARDDAV_NS);
		writer.setPrefix(CardDavSchema.CALENDARSERVER_PREFIX, CardDavSchema.CALENDARSERVER_NS);
		return writer;
	}

	/**
	 * Renders a fragment with {@link #openFragment(OutputStream)} bindings into a byte array.
	 */
	static byte[] renderFragment(FragmentRenderer renderer) throws XMLStreamException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		XMLStreamWriter writer = openFragment(buffer);
		renderer.render(writer);
		writer.flush();
		writer.close();
		return buffer.toByteArray();
	}

	/**
	 * Callback writing a fragment.
	 */
	@FunctionalInterface
	interface FragmentRenderer {
		void render(XMLStreamWriter writer) throws XMLStreamException;
	}

	/**
	 * The writer for content rendered per request.
	 */
	public XMLStreamWriter xml() {
		return _xml;
	}

	/**
	 * Splices a pre-rendered fragment into the output.
	 *
	 * <p>
	 * Must only be called when the last element written to {@link #xml()} is
	 * complete (no start tag waiting for attributes or content), e.g. directly
	 * after a {@link XMLStreamWriter#writeEndElement()}.
	 * </p>
	 */
	public void writeRaw(byte[] fragment) throws XMLStreamException, IOException {
		_xml.flush();
		_out.write(fragment);
	}

	/**
	 * Writes the {@code </d:multistatus>} end tag and flushes the output.
	 */
	public void close() throws XMLStreamException, IOException {
		_xml.flush();
		_xml.close();
		_out.write(FOOTER);
		_out.flush();
	}
}
