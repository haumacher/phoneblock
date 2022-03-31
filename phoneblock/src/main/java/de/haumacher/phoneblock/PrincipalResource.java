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
public class PrincipalResource extends Resource {

	private String _principal;

	/** 
	 * Creates a {@link PrincipalResource}.
	 */
	public PrincipalResource(String rootUrl, String resourcePath, String principal) {
		super(rootUrl, resourcePath);
		_principal = principal;
	}
	
	@Override
	protected int fillProperty(Element propElement, Element propertyElement, QName property) {
		if (ContactServlet.ADDRESSBOOK_HOME_SET.equals(property)) {
			Element container = appendElement(propElement, property);
			DomUtil.appendTextElement(container, ContactServlet.DAV_HREF, url(ContactServlet.ADDRESSES_PATH + _principal + "/"));
			return HttpServletResponse.SC_OK;
		}
		return super.fillProperty(propElement, propertyElement, property);
	}

}
