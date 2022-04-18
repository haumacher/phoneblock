/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import static de.haumacher.phoneblock.util.DomUtil.*;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.carddav.schema.DavSchema;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.DomUtil;

/**
 * {@link Resource} representing a PhoneBlock user account.
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
	public void propfind(HttpServletRequest req, Element multistatus, List<Element> properties) {
		DBService.getInstance().updateLastAccess(_principal, System.currentTimeMillis());
		
		super.propfind(req, multistatus, properties);
	}
	
	@Override
	public int fillProperty(HttpServletRequest req, Element propElement, Element propertyElement, QName property) {
		if (CardDavSchema.CARDDAV_ADDRESSBOOK_HOME_SET.equals(property)) {
			Element container = appendElement(propElement, property);
			DomUtil.appendTextElement(container, DavSchema.DAV_HREF, url(CardDavServlet.ADDRESSES_PATH + _principal + "/"));
			return HttpServletResponse.SC_OK;
		}
		return super.fillProperty(req, propElement, propertyElement, property);
	}

}
