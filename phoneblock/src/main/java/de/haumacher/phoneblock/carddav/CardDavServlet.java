/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav;

import static de.haumacher.phoneblock.util.DomUtil.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;

import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.carddav.resource.AddressBookCache;
import de.haumacher.phoneblock.carddav.resource.AddressBookResource;
import de.haumacher.phoneblock.carddav.resource.PrincipalResource;
import de.haumacher.phoneblock.carddav.resource.Resource;
import de.haumacher.phoneblock.carddav.resource.RootResource;
import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.carddav.schema.DavSchema;
import de.haumacher.phoneblock.util.DebugUtil;

/**
 * {@link HttpServlet} serving the CardDAV address book(s).
 */
@WebServlet(urlPatterns = {CardDavServlet.DIR_NAME, CardDavServlet.URL_PATTERN})
public class CardDavServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(CardDavServlet.class);
	
	static final String DIR_NAME = "/contacts";

	private static final String BASE_PATH = DIR_NAME + "/";

	private static final String METHOD_REPORT = "REPORT";

	private static final String METHOD_PROPFIND = "PROPFIND";

	/**
	 * URL pattern of URLs the {@link CardDavServlet} processes.
	 */
	public static final String URL_PATTERN = BASE_PATH + "*";

	private static final int SC_MULTI_STATUS = 207;
	
	/**
	 * The request path where user-specific information is served.
	 */
	public static final String PRINCIPALS_PATH = "/principals/";

	/**
	 * The path relative to the servlet's #URL_PATTERN} where address information is located.
	 */
	public static final String ADDRESSES_PATH = "/addresses/";

	public static final String SERVER_LOC = "https://phoneblock.haumacher.de";

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (req.getPathInfo() == null && req.getServletPath().equals(DIR_NAME)) {
			resp.sendRedirect(req.getContextPath() + BASE_PATH);
			return;
		}
		
		try {
			if (METHOD_PROPFIND.equals(req.getMethod())) {
				doPropfind(req, resp);
			}
			else if (METHOD_REPORT.equals(req.getMethod())) {
				doReport(req, resp);
			}
			else {
				super.service(req, resp);
			}
		} catch (Exception ex) {
			LOG.error("Failed to process CardDAV request.", ex);
			
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}

	@Override
	protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setHeader("Allow", 
			"GET" + ", " + "HEAD" + ", " + METHOD_PROPFIND + ", " + METHOD_REPORT + ", " + "TRACE" + ", " + "OPTIONS");
		resp.setHeader("DAV", "addressbook");
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		Resource resource = getResource(req);
		if (resource == null) {
			handleNotFound(req, resp);
			return;
		}
		
		if (LOG.isDebugEnabled()) {
			StringWriter out = new StringWriter();
			out.write(req.getMethod() + " " + req.getPathInfo() + ": " + resource);
			out.write('\n');
			DebugUtil.dumpHeaders(out, req);
			LOG.debug(out.toString());
		}

		resource.get(req, resp);
	}
	
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		Resource resource = getResource(req);
		if (resource == null) {
			handleNotFound(req, resp);
			return;
		}

		if (LOG.isDebugEnabled()) {
			StringWriter out = new StringWriter();
			out.write(req.getMethod() + " " + req.getPathInfo() + ": " + resource);
			out.write('\n');
			DebugUtil.dumpHeaders(out, req);
			LOG.debug(out.toString());
		}
		
		resource.put(req, resp);
	}
	
	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		Resource resource = getResource(req);
		if (resource == null) {
			handleNotFound(req, resp);
			return;
		}

		if (LOG.isDebugEnabled()) {
			StringWriter out = new StringWriter();
			out.write(req.getMethod() + " " + req.getPathInfo() + ": " + resource);
			out.write('\n');
			DebugUtil.dumpHeaders(out, req);
			LOG.debug(out.toString());
		}

		resource.delete(resp);
	}

	private void doPropfind(HttpServletRequest req, HttpServletResponse resp) throws IOException, SAXException {
		Resource resource = getResource(req);
		if (resource == null) {
			handleNotFound(req, resp);
			return;
		}

		DocumentBuilder builder = createDocumentBuilder();
		Document requestDoc = builder.parse(req.getInputStream());
		Depth depth = Depth.fromHeader(req.getHeader("depth"));
		List<QName> properties = qnames(toList(elements(requestDoc, DavSchema.DAV_PROPFIND, DavSchema.DAV_PROP)));

		if (LOG.isDebugEnabled()) {
			StringWriter out = new StringWriter();
			out.write(req.getMethod() + " " + req.getPathInfo() + " " + depth + ": " + properties);
			out.write('\n');
			DebugUtil.dumpHeaders(out, req);
			LOG.debug(out.toString());
		}

		Document responseDoc = builder.newDocument();
		Element multistatus = appendElement(responseDoc, DavSchema.DAV_MULTISTATUS);
		multistatus.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE, DavSchema.DAV_NS);
		multistatus.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE + ':' + CardDavSchema.CARDDAV_PREFIX, CardDavSchema.CARDDAV_NS);

		resource.propfind(req, null, multistatus, properties);
		if (depth != Depth.EMPTY) {
			for (Resource content : resource.list()) {
				content.propfind(req, resource, multistatus, properties);
			}
		}
		
		marshalMultiStatus(resp, responseDoc);
	}

	private void marshalMultiStatus(HttpServletResponse resp,
			Document responseDoc) throws IOException {
		resp.setStatus(SC_MULTI_STATUS);
		resp.setCharacterEncoding("utf-8");
		resp.setContentType("application/xml");
		
		DOMImplementationLS ls = (DOMImplementationLS) responseDoc.getImplementation().getFeature("LS", "3.0");
		LSOutput output = ls.createLSOutput();
		output.setEncoding("utf-8");
		output.setByteStream(resp.getOutputStream());
		LSSerializer serializer = ls.createLSSerializer();
		serializer.write(responseDoc, output);
		
		if (LOG.isDebugEnabled()) {
			StringWriter out = new StringWriter();
			DebugUtil.dumpDoc(out, responseDoc);
			LOG.debug("Response: " + out.toString());
		}
	}

	private void doReport(HttpServletRequest req, HttpServletResponse resp) throws IOException, SAXException {
		Resource resource = getResource(req);
		if (resource == null) {
			handleNotFound(req, resp);
			return;
		}
		
		DocumentBuilder builder = createDocumentBuilder();
		Document requestDoc = builder.parse(req.getInputStream());
		if (CardDavSchema.CARDDAV_ADDRESSBOOK_MULTIGET.equals(qname(requestDoc.getDocumentElement()))) {
			List<Element> properties = toList(elements(requestDoc, CardDavSchema.CARDDAV_ADDRESSBOOK_MULTIGET, DavSchema.DAV_PROP));
			
			if (LOG.isDebugEnabled()) {
				StringWriter out = new StringWriter();
				out.write(req.getMethod() + " " + req.getPathInfo() + ": " + toList(qnames(properties)));
				out.write('\n');
				DebugUtil.dumpHeaders(out, req);
				DebugUtil.dumpDoc(out, requestDoc);
				LOG.debug(out.toString());
			}
			
			Document responseDoc = builder.newDocument();
			Element multistatus = appendElement(responseDoc, DavSchema.DAV_MULTISTATUS);
			multistatus.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE, DavSchema.DAV_NS);
			multistatus.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE + ':' + CardDavSchema.CARDDAV_PREFIX, CardDavSchema.CARDDAV_NS);

			for (Element href : filter(elements(filter(elements(requestDoc), CardDavSchema.CARDDAV_ADDRESSBOOK_MULTIGET)), DavSchema.DAV_HREF)) {
				String url = href.getTextContent();
				
				Element response = appendElement(multistatus, DavSchema.DAV_RESPONSE);
				appendTextElement(response, DavSchema.DAV_HREF, url);
				
				Resource content = resource.get(url);
				
				Element propstat = appendElement(response, DavSchema.DAV_PROPSTAT);
				if (content != null) {
					Element propElement = appendElement(propstat, DavSchema.DAV_PROP);
					
					String etag = content.getEtag();
					if (etag != null) {
						Element container = appendElement(propElement, DavSchema.DAV_GETETAG);
						appendText(container, Resource.quote(etag));
					}
					
					for (Element property : properties) {
						QName qname = qname(property);
						if (DavSchema.DAV_GETETAG.equals(qname)) {
							// Unconditionally added above.
							continue;
						}
						
						content.fillProperty(req, propElement, qname);
					}
					appendTextElement(propstat, DavSchema.DAV_STATUS, "HTTP/1.1 " + HttpServletResponse.SC_OK + " " + EnglishReasonPhraseCatalog.INSTANCE.getReason(HttpServletResponse.SC_OK, null));
				} else {
					appendTextElement(propstat, DavSchema.DAV_STATUS, "HTTP/1.1 " + HttpServletResponse.SC_NOT_FOUND + " " + EnglishReasonPhraseCatalog.INSTANCE.getReason(HttpServletResponse.SC_NOT_FOUND, null));
				}
			}
			
			marshalMultiStatus(resp, responseDoc);
		} else {
			StringWriter out = new StringWriter();
			DebugUtil.dumpMethod(out, req);
			DebugUtil.dumpHeaders(out, req);
			DebugUtil.dumpDoc(out, requestDoc);
			LOG.warn("Not implemented: " + out.toString());

			resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
		}
	}

	private void handleNotFound(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		LOG.warn("Not found: " + DebugUtil.dumpRequestFull(req));
			
		resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
	}

	private Resource getResource(HttpServletRequest req) {
		String serverRoot = req.getContextPath() + req.getServletPath();
		
		String rootUrl = CardDavServlet.SERVER_LOC + serverRoot;
		String resourcePath = req.getPathInfo();

		if ("/".equals(resourcePath)) {
			return new RootResource(rootUrl, resourcePath);
		}
		
		else if (resourcePath.startsWith(PRINCIPALS_PATH)) {
			String principal = resourcePath.substring(PRINCIPALS_PATH.length());
			if (isAuthenticated(req, principal)) {
				return new PrincipalResource(rootUrl, resourcePath, principal);
			} else {
				LOG.warn("Wrong authentication for resource: " + resourcePath);
				return null;
			}
		}
		
		else if (resourcePath.startsWith(ADDRESSES_PATH)) {
			int endIdx = resourcePath.indexOf('/', ADDRESSES_PATH.length());
			if (endIdx < 0) {
				LOG.warn("No principal found in address path: " + resourcePath);
				return null;
			}
			
			String principal = resourcePath.substring(ADDRESSES_PATH.length(), endIdx);
			if (isAuthenticated(req, principal)) {
				AddressBookResource addressBook = 
						AddressBookCache.getInstance().lookupAddressBook(rootUrl, serverRoot, resourcePath, principal);
				
				if (endIdx < resourcePath.length() - 1) {
					return addressBook.lookupAddress(resourcePath.substring(endIdx + 1));
				} else {
					return addressBook;
				}
			} else {
				LOG.warn("Wrong authentication for resource: " + resourcePath);
				return null;
			}
		} else {
			LOG.warn("Addressbook resource not found: " + resourcePath);
			return null;
		}
	}

	private boolean isAuthenticated(HttpServletRequest req, String principal) {
		return principal.equals(req.getAttribute(LoginFilter.AUTHENTICATED_USER_ATTR));
	}

}
