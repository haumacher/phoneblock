/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import static de.haumacher.phoneblock.util.DomUtil.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;

import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.w3c.dom.Element;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.carddav.schema.DavSchema;

/**
 * Base class for CardDAV resources.
 */
public abstract class Resource {

	private final String _rootUrl;
	
	private final String _resourcePath;
	
	/** 
	 * Creates a {@link Resource}.
	 *
	 * @param rootUrl The base URL of the WEBDAV servlet.
	 * @param resourcePath The path of this resource relative to the root URL.
	 */
	public Resource(String rootUrl, String resourcePath) {
		super();
		_rootUrl = rootUrl;
		_resourcePath = resourcePath;
	}
	
	/**
	 * The base URL of the WEBDAV servlet.
	 */
	public String getRootUrl() {
		return _rootUrl;
	}
	
	/**
	 * The path of this resource relative to the root URL.
	 * 
	 * @see #getRootUrl()
	 */
	public String getResourcePath() {
		return _resourcePath;
	}

	/**
	 * Answers the <code>propfind</code> request.
	 * 
	 * @param req
	 *        The current request.
	 * @param multistatus
	 *        The result element to add the response to.
	 * @param properties
	 *        The {@link Element}s describing the properties to retrieve.
	 */
	public void propfind(HttpServletRequest req, Element multistatus, List<Element> properties) {
		Element response = appendElement(multistatus, DavSchema.DAV_RESPONSE);
		appendTextElement(response, DavSchema.DAV_HREF, url());
		for (Element property : properties) {
			Element propstat = appendElement(response, DavSchema.DAV_PROPSTAT);
			Element prop = appendElement(propstat, DavSchema.DAV_PROP);
			int status = fillProperty(req, prop, property);
			appendTextElement(propstat, DavSchema.DAV_STATUS, "HTTP/1.1 " + status + " " + EnglishReasonPhraseCatalog.INSTANCE.getReason(status, null));
		}
	}

	/**
	 * Fills the given property container element with property information.
	 * @param req
	 *        The current request.
	 * @param propElement
	 *        The {@link Element} to fill with property information.
	 * @param propertyElement
	 *        The qualified name of the property to read.
	 * @return The status code for the request.
	 */
	public final int fillProperty(HttpServletRequest req, Element propElement, Element propertyElement) {
		return fillProperty(req, propElement, propertyElement, qname(propertyElement));
	}

	/**
	 * Fills the given property container element with property information.
	 * @param req
	 *        The current request.
	 * @param propElement
	 *        The {@link Element} to fill with property information.
	 * @param propertyElement
	 *        The qualified name of the property to read.
	 * @param property
	 *        The qualified name of the property to retrieve.
	 * @return The status code for the request.
	 */
	public int fillProperty(HttpServletRequest req, Element propElement, Element propertyElement, QName property) {
		if (DavSchema.DAV_CURRENT_USER_PRINCIPAL.equals(property)) {
			String userName = (String) req.getAttribute(LoginFilter.AUTHENTICATED_USER_ATTR);
			if (userName != null) {
				Element container = appendElement(propElement, DavSchema.DAV_CURRENT_USER_PRINCIPAL);
				appendTextElement(container, DavSchema.DAV_HREF, url(CardDavServlet.PRINCIPALS_PATH + userName));
				return HttpServletResponse.SC_OK;
			}
		}
		else if (DavSchema.DAV_DISPLAYNAME.equals(property)) {
			String displayName = getDisplayName();
			if (displayName != null) {
				Element container = appendElement(propElement, DavSchema.DAV_DISPLAYNAME);
				appendText(container, displayName);
				return HttpServletResponse.SC_OK;
			}
		}
		else if (DavSchema.DAV_RESOURCETYPE.equals(property)) {
			Element container = appendElement(propElement, DavSchema.DAV_RESOURCETYPE);
			if (isCollection()) {
				appendElement(container, DavSchema.DAV_COLLECTION);
			}
			QName type = getResourceType();
			if (type != null) {
				appendElement(container, type);
			}
			return HttpServletResponse.SC_OK;
		}
		else if (DavSchema.DAV_GETETAG.equals(property)) {
			String etag = getEtag();
			if (etag != null) {
				Element container = appendElement(propElement, DavSchema.DAV_GETETAG);
				appendText(container, quote(etag));
				return HttpServletResponse.SC_OK;
			}
		}
		return HttpServletResponse.SC_NOT_FOUND;
	}

	/**
	 * The display name of this resource. 
	 */
	protected String getDisplayName() {
		int sepIndex = _resourcePath.lastIndexOf('/');
		if (sepIndex < 0) {
			return _resourcePath;
		}
		return _resourcePath.substring(sepIndex + 1);
	}

	/** 
	 * The resource type of this resource.
	 */
	protected QName getResourceType() {
		return null;
	}

	/** 
	 * Whether this resource is a WEBDAV collection.
	 * 
	 * @see #list()
	 */
	protected boolean isCollection() {
		return false;
	}

	/**
	 * Quotes the given <code>etag</code> contents producing an <code>etag</code> string.
	 * 
	 * @see #getEtag()
	 */
	public static String quote(String etag) {
		return '"' + etag.replace("\"", "\\\"") + '"';
	}

	/**
	 * The unquoted <code>etag</code> content.
	 * 
	 * @see #quote(String)
	 */
	public String getEtag() {
		return null;
	}

	protected final String url(String suffix) {
		return _rootUrl + suffix;
	}

	/** 
	 * All sub-resources, if this is a collection resource.
	 * 
	 * @see #isCollection()
	 */
	public Collection<Resource> list() {
		return Collections.emptyList();
	}

	/** 
	 * The URL of this resource.
	 */
	public String url() {
		return _rootUrl + _resourcePath;
	}

	/** 
	 * The sub-resource with the given URL, if this is a collection.
	 * 
	 * @param url URL of a child resource to retrieve.
	 * @return The URL of the child resource, or <code>null</code> of no such child exists.
	 * 
	 * @see #isCollection()
	 */
	public Resource get(String url) {
		return null;
	}

	/** 
	 * Requests to delete this resource.
	 */
	public void delete(HttpServletResponse resp) {
		resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
	}

	/**
	 * Creates this resource from the data in the given request.
	 * 
	 * @param req
	 *        The request that carries the <code>vcard</code> data.
	 * @param resp
	 *        The response to report errors to.
	 * 
	 * @throws IOException
	 *         If reading the content fails.
	 */
	public void put(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
	}
}
