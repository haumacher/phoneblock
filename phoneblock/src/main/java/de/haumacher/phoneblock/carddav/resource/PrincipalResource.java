/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.carddav.schema.DavSchema;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link Resource} representing a PhoneBlock user account.
 */
public class PrincipalResource extends Resource {

	private final String _principal;

	/**
	 * Creates a {@link PrincipalResource}.
	 */
	public PrincipalResource(String rootUrl, String resourcePath, String principal) {
		super(rootUrl, resourcePath);
		_principal = principal;
	}

	@Override
	public int fillProperty(RenderContext ctx, XMLStreamWriter writer, QName property)
			throws XMLStreamException {
		if (CardDavSchema.CARDDAV_ADDRESSBOOK_HOME_SET.equals(property)) {
			writer.writeStartElement(CardDavSchema.CARDDAV_NS, "addressbook-home-set");
			writer.writeStartElement(DavSchema.DAV_NS, "href");
			writer.writeCharacters(url(CardDavServlet.ADDRESSES_PATH + _principal + "/"));
			writer.writeEndElement();
			writer.writeEndElement();
			return HttpServletResponse.SC_OK;
		}
		return super.fillProperty(ctx, writer, property);
	}

}
