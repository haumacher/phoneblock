/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock;

import static de.haumacher.phoneblock.DomUtil.*;

import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;

import org.w3c.dom.Element;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class AddressResource extends Resource {

	/** 
	 * Creates a {@link AddressResource}.
	 */
	public AddressResource(String rootUrl, String resourcePath) {
		super(rootUrl, resourcePath);
	}
	
	@Override
	protected QName getResourceType() {
		return ContactServlet.CARDDAV_ADDRESS_DATA;
	}

	@Override
	protected String getEtag() {
		return "1";
	}
	
	@Override
	protected int fillProperty(Element propElement, Element propertyElement, QName property) {
		if (ContactServlet.CARDDAV_ADDRESS_DATA.equals(property)) {
			Element container = appendElement(propElement, property);
			appendText(container, 
				"BEGIN:VCARD\n"
				+ "VERSION:3.0\n"
				+ "UID:" + getDisplayName() + "\n"
				+ "FN:" + getDisplayName() + "\n"
				+ "TEL;TYPE=WORK:4711\n"
				+ "END:VCARD");
			return HttpServletResponse.SC_OK;
		}
		return super.fillProperty(propElement, propertyElement, property);
	}
}
