/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.carddav.schema.DavSchema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Base class for CardDAV resources.
 */
public abstract class Resource {

	private static final Logger LOG = LoggerFactory.getLogger(Resource.class);

	private final String _rootUrl;

	private final String _resourcePath;

	private static final Map<QName, QName> UNSUPPORTED_KNOWN = Arrays.asList(
		new QName("DAV:", "add-member"),
		new QName("DAV:", "current-user-privilege-set"),
		new QName("DAV:", "getcontenttype"),
		new QName("DAV:", "group-membership"),
		new QName("DAV:", "owner"),
		new QName("DAV:", "principal-collection-set"),
		new QName("DAV:", "principal-URL"),
		new QName("DAV:", "quota-available-bytes"),
		new QName("DAV:", "quota-used-bytes"),
		new QName("DAV:", "resource-id"),
		new QName("DAV:", "supported-report-set"),
		new QName("DAV:", "sync-token"),
		new QName("http://apple.com/ns/ical/", "calendar-color"),
		new QName("http://calendarserver.org/ns/", "email-address-set"),
		new QName("http://calendarserver.org/ns/", "me-card"),
		new QName("http://calendarserver.org/ns/", "pushkey"),
		new QName("http://calendarserver.org/ns/", "push-transports"),
		new QName("http://calendarserver.org/ns/", "source"),
		new QName("http://calendarserver.org/ns/", "xmpp-uri"),
		new QName("http://icewarp.com/ns/", "conference-support"),
		new QName("http://icewarp.com/ns/", "default-calendar-URL"),
		new QName("http://icewarp.com/ns/", "default-contacts-URL"),
		new QName("http://icewarp.com/ns/", "default-notes-URL"),
		new QName("http://icewarp.com/ns/", "default-tasks-URL"),
		new QName("http://me.com/_namespace/", "bulk-requests"),
		new QName("http://me.com/_namespace/", "guardian-restricted"),
		new QName("urn:ietf:params:xml:ns:caldav", "calendar-home-set"),
		new QName("urn:ietf:params:xml:ns:caldav", "calendar-user-address-set"),
		new QName("urn:ietf:params:xml:ns:caldav", "schedule-inbox-URL"),
		new QName("urn:ietf:params:xml:ns:caldav", "schedule-outbox-URL"),
		new QName("urn:ietf:params:xml:ns:carddav", "addressbook-description"),
		new QName("urn:ietf:params:xml:ns:carddav", "directory-gateway"),
		new QName("urn:ietf:params:xml:ns:carddav", "max-image-size"),
		new QName("urn:ietf:params:xml:ns:carddav", "max-resource-size"),
		new QName("urn:ietf:params:xml:ns:carddav", "supported-address-data"),
		new QName("urn:ietf:params:xml:ns:caldav", "calendar-description"),
		new QName("urn:ietf:params:xml:ns:caldav", "supported-calendar-component-set"),
		new QName("urn:mobileme:davservices", "quota-available"),
		new QName("urn:mobileme:davservices", "quota-used")
	).stream().collect(Collectors.toConcurrentMap(x -> x, x -> x));

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
	 * Writes the {@code <d:response>} block of this resource into the given
	 * multistatus stream.
	 *
	 * @param ctx
	 *        Render context (authenticated user, etc.).
	 * @param parent
	 *        The parent resource in which context a property of this resource
	 *        was requested. {@code null}, if this is the top-level requested
	 *        resource.
	 * @param writer
	 *        The {@link XMLStreamWriter} positioned inside the open
	 *        {@code <d:multistatus>} element.
	 * @param properties
	 *        The qualified names of the properties to retrieve.
	 */
	public void propfind(RenderContext ctx, Resource parent, XMLStreamWriter writer,
			List<QName> properties) throws XMLStreamException {
		writer.writeStartElement(DavSchema.DAV_NS, "response");
		writer.writeStartElement(DavSchema.DAV_NS, "href");
		writer.writeCharacters(url(parent));
		writer.writeEndElement();
		for (QName property : properties) {
			writer.writeStartElement(DavSchema.DAV_NS, "propstat");
			writer.writeStartElement(DavSchema.DAV_NS, "prop");
			int status = fillProperty(ctx, writer, property);
			writer.writeEndElement();
			writer.writeStartElement(DavSchema.DAV_NS, "status");
			writer.writeCharacters("HTTP/1.1 " + status + " "
				+ EnglishReasonPhraseCatalog.INSTANCE.getReason(status, null));
			writer.writeEndElement();
			writer.writeEndElement();
		}
		writer.writeEndElement();
	}

	/**
	 * Writes the contents of the property {@code property} into the open
	 * {@code <d:prop>} element on {@code writer}, returning the WebDAV property
	 * status code.
	 *
	 * <p>
	 * Implementations must either write the property element entirely (and
	 * return {@link HttpServletResponse#SC_OK}) or write nothing (and return
	 * {@link HttpServletResponse#SC_NOT_FOUND}).
	 * </p>
	 */
	public int fillProperty(RenderContext ctx, XMLStreamWriter writer, QName property)
			throws XMLStreamException {
		if (DavSchema.DAV_CURRENT_USER_PRINCIPAL.equals(property)) {
			String userName = ctx.authenticatedUser();
			if (userName != null) {
				writer.writeStartElement(DavSchema.DAV_NS, "current-user-principal");
				writer.writeStartElement(DavSchema.DAV_NS, "href");
				writer.writeCharacters(url(CardDavServlet.PRINCIPALS_PATH + userName));
				writer.writeEndElement();
				writer.writeEndElement();
				return HttpServletResponse.SC_OK;
			}
		}
		else if (DavSchema.DAV_DISPLAYNAME.equals(property)) {
			String displayName = getDisplayName();
			if (displayName != null) {
				writer.writeStartElement(DavSchema.DAV_NS, "displayname");
				writer.writeCharacters(displayName);
				writer.writeEndElement();
				return HttpServletResponse.SC_OK;
			}
		}
		else if (DavSchema.DAV_RESOURCETYPE.equals(property)) {
			writer.writeStartElement(DavSchema.DAV_NS, "resourcetype");
			if (isCollection()) {
				writer.writeEmptyElement(DavSchema.DAV_NS, "collection");
			}
			QName type = getResourceType();
			if (type != null) {
				writer.writeEmptyElement(type.getNamespaceURI(), type.getLocalPart());
			}
			writer.writeEndElement();
			return HttpServletResponse.SC_OK;
		}
		else if (DavSchema.DAV_GETETAG.equals(property)) {
			String etag = getEtag();
			if (etag != null) {
				writer.writeStartElement(DavSchema.DAV_NS, "getetag");
				writer.writeCharacters(quote(etag));
				writer.writeEndElement();
				return HttpServletResponse.SC_OK;
			}
		}
		else if (CardDavSchema.CALENDARSERVER_GETCTAG.equals(property)) {
			String etag = getEtag();
			if (etag != null) {
				writer.writeStartElement(CardDavSchema.CALENDARSERVER_NS, "getctag");
				writer.writeCharacters(etag);
				writer.writeEndElement();
				return HttpServletResponse.SC_OK;
			}
		}

		if (UNSUPPORTED_KNOWN.putIfAbsent(property, property) == null) {
			LOG.warn("Discovered new unsupported DAV property '" + property + "': " + _resourcePath);
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
	public Collection<? extends Resource> list() {
		return Collections.emptyList();
	}

	/**
	 * The URL of this resource.
	 *
	 * @param parent
	 *        The parent {@link Resource} from which a relative url can be constructed. <code>null</code> to construct
	 *        an absolute URL.
	 */
	public String url(Resource parent) {
		// Note: Using short relative URLs prevents the Fritz!Box from issuing "DELETE" commands
		// when a user removes a number from his block list. The effect is that the number is re-added
		// to his block list upon the next synchronization. It is currently unclear what's the
		// problem, since loading and adding new numbers works fine.
		//
		// if (parent != null) {
		// 	return _resourcePath.substring(parent.getResourcePath().length());
		// }
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

	/**
	 * Retrieves the content of this resource.
	 *
	 * @param req
	 *        The request.
	 * @param resp
	 *        The response where the contents is written to.
	 *
	 * @throws IOException
	 *         If reading the content fails.
	 */
	public void get(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
	}
}
