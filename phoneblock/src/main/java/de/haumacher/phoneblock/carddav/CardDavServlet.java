/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav;

import static de.haumacher.phoneblock.util.DomUtil.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;

import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.carddav.resource.AddressBookResource;
import de.haumacher.phoneblock.carddav.resource.AddressResource;
import de.haumacher.phoneblock.carddav.resource.PrincipalResource;
import de.haumacher.phoneblock.carddav.resource.Resource;
import de.haumacher.phoneblock.carddav.resource.RootResource;
import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.carddav.schema.DavSchema;

/**
 * {@link HttpServlet} serving the CardDAV address book(s).
 */
@WebServlet(urlPatterns = CardDavServlet.URL_PATTERN)
public class CardDavServlet extends HttpServlet {

	private static final String METHOD_REPORT = "REPORT";

	private static final String METHOD_PROPFIND = "PROPFIND";

	/**
	 * URL pattern of URLs the {@link CardDavServlet} processes.
	 */
	public static final String URL_PATTERN = "/contacts/*";

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
		try {
			if (METHOD_PROPFIND.equals(req.getMethod())) {
				doPropfind(req, resp);
			}
			else if (METHOD_REPORT.equals(req.getMethod())) {
				doReport(req, resp);
			}
			else {
				dumpMethod(req);
				dumpParams(req);
				dumpHeaders(req);
				super.service(req, resp);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
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
		
		resource.get(req, resp);
	}
	
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		Resource resource = getResource(req);
		if (resource == null) {
			handleNotFound(req, resp);
			return;
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

		resource.delete(resp);
	}

	private void doPropfind(HttpServletRequest req, HttpServletResponse resp) throws IOException, SAXException {
		Resource resource = getResource(req);
		if (resource == null) {
			handleNotFound(req, resp);
			return;
		}

		Document requestDoc = getBuilder().parse(req.getInputStream());
		Depth depth = Depth.fromHeader(req.getHeader("depth"));
		List<Element> properties = toList(elements(requestDoc, DavSchema.DAV_PROPFIND, DavSchema.DAV_PROP));

		System.out.println(req.getMethod() + " " + req.getPathInfo() + " " + depth + ": " + toList(qnames(properties)));
		dumpHeaders(req);

		Document responseDoc = getBuilder().newDocument();
		Element multistatus = appendElement(responseDoc, DavSchema.DAV_MULTISTATUS);

		resource.propfind(req, multistatus, properties);
		if (depth != Depth.EMPTY) {
			for (Resource content : resource.list()) {
				content.propfind(req, multistatus, properties);
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
		
		System.out.println(">>>");
		dumpDoc(responseDoc);
		System.out.println();
		System.out.println(">>>");
	}

	private void doReport(HttpServletRequest req, HttpServletResponse resp) throws IOException, SAXException {
		Resource resource = getResource(req);
		if (resource == null) {
			handleNotFound(req, resp);
			return;
		}
		
		Document requestDoc = getBuilder().parse(req.getInputStream());
		if (CardDavSchema.CARDDAV_ADDRESSBOOK_MULTIGET.equals(qname(requestDoc.getDocumentElement()))) {
			List<Element> properties = toList(elements(requestDoc, CardDavSchema.CARDDAV_ADDRESSBOOK_MULTIGET, DavSchema.DAV_PROP));
			
			System.out.println(req.getMethod() + " " + req.getPathInfo() + ": " + toList(qnames(properties)));
			dumpHeaders(req);
			dumpRequestDoc(requestDoc);
			
			Document responseDoc = getBuilder().newDocument();
			Element multistatus = appendElement(responseDoc, DavSchema.DAV_MULTISTATUS);

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
						
						content.fillProperty(req, propElement, property, qname);
					}
					appendTextElement(propstat, DavSchema.DAV_STATUS, "HTTP/1.1 " + HttpServletResponse.SC_OK + " " + EnglishReasonPhraseCatalog.INSTANCE.getReason(HttpServletResponse.SC_OK, null));
				} else {
					appendTextElement(propstat, DavSchema.DAV_STATUS, "HTTP/1.1 " + HttpServletResponse.SC_NOT_FOUND + " " + EnglishReasonPhraseCatalog.INSTANCE.getReason(HttpServletResponse.SC_NOT_FOUND, null));
				}
			}
			
			marshalMultiStatus(resp, responseDoc);
		} else {
			dumpMethod(req);
			dumpHeaders(req);
			dumpRequestDoc(requestDoc);

			resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
		}
	}

	private void handleNotFound(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		dump(req);
		
		System.out.println(">>> 404 >>>");
		resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
	}

	private Resource getResource(HttpServletRequest req) {
		String serverRoot = req.getContextPath() + req.getServletPath();
		
		String rootUrl = CardDavServlet.SERVER_LOC + serverRoot;
		String resourcePath = req.getPathInfo();

		if ("/".equals(resourcePath)) {
			return new RootResource(rootUrl, resourcePath);
		}
		
		if (resourcePath.startsWith(PRINCIPALS_PATH)) {
			String principal = resourcePath.substring(PRINCIPALS_PATH.length());
			if (isAuthenticated(req, principal)) {
				return new PrincipalResource(rootUrl, resourcePath, principal);
			}
		}
		
		if (resourcePath.startsWith(ADDRESSES_PATH)) {
			int endIdx = resourcePath.indexOf('/', ADDRESSES_PATH.length());
			if (endIdx < 0) {
				return null;
			}
			
			String principal = resourcePath.substring(ADDRESSES_PATH.length(), endIdx);
			if (isAuthenticated(req, principal)) {
				if (endIdx < resourcePath.length() - 1) {
					return new AddressResource(rootUrl, resourcePath, principal);
				} else {
					return new AddressBookResource(rootUrl, serverRoot, resourcePath, principal);
				}
			}
		}
		
		// Not found.
		return null;
	}

	private boolean isAuthenticated(HttpServletRequest req, String principal) {
		return principal.equals(req.getAttribute(LoginFilter.AUTHENTICATED_USER_ATTR));
	}

	private void dumpRequestDoc(Document requestDoc) {
		System.out.println("<<<");
		dumpDoc(requestDoc);
		System.out.println();
		System.out.println("<<<");
	}

	private void dumpDoc(Document doc) {
		DOMImplementationLS ls = (DOMImplementationLS) doc.getImplementation().getFeature("LS", "3.0");
		LSOutput debug = ls.createLSOutput();
		debug.setEncoding("utf-8");
		debug.setByteStream(System.out);
		LSSerializer serializer = ls.createLSSerializer();
		serializer.write(doc, debug);
	}

	private void dump(HttpServletRequest req) throws IOException {
		dumpMethod(req);
		dumpParams(req);
		dumpHeaders(req);
		dumpContent(req);
	}

	private void dumpContent(HttpServletRequest req) throws IOException {
		System.out.println("<<<");
		BufferedReader reader = req.getReader();
		String line;
		while ((line = reader.readLine()) != null) {
			System.out.println(line);
		}
		System.out.println("<<<");
		System.out.println();
	}

	private void dumpMethod(HttpServletRequest req) {
		System.out.println(req.getMethod() + " " + req.getPathInfo());
	}

	private void dumpParams(HttpServletRequest req) {
		for (Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
			System.out.println("  P: " + entry.getKey() + ": " + Arrays.asList(entry.getValue()));
		}
	}

	private void dumpHeaders(HttpServletRequest req) {
		for (Enumeration<String> keyIt = req.getHeaderNames(); keyIt.hasMoreElements(); ) {
			String key = keyIt.nextElement();
			for (Enumeration<String> valueIt = req.getHeaders(key); valueIt.hasMoreElements(); ) {
				System.out.println("  H: " + key + ": " + valueIt.nextElement());
			}
		}
	}

}
