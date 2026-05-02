/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.carddav.CardDavRequestParser.MultiGetRequest;
import de.haumacher.phoneblock.carddav.resource.AddressBookCache;
import de.haumacher.phoneblock.carddav.resource.AddressBookCollectionResource;
import de.haumacher.phoneblock.carddav.resource.AddressBookResource;
import de.haumacher.phoneblock.carddav.resource.MultiStatusWriter;
import de.haumacher.phoneblock.carddav.resource.PrincipalResource;
import de.haumacher.phoneblock.carddav.resource.RenderContext;
import de.haumacher.phoneblock.carddav.resource.Resource;
import de.haumacher.phoneblock.carddav.resource.RootResource;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.util.DebugUtil;
import de.haumacher.phoneblock.util.EtagUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link HttpServlet} serving the CardDAV address book(s).
 */
@WebServlet(urlPatterns = {CardDavServlet.DIR_NAME, CardDavServlet.URL_PATTERN})
public class CardDavServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(CardDavServlet.class);

	public static final String DIR_NAME = "/contacts";

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

	public static final String SERVER_LOC = "https://phoneblock.net";

	private static final Pattern WHITE_SPACE_PREFIX = Pattern.compile("/\\s*/");

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

	private void doPropfind(HttpServletRequest req, HttpServletResponse resp) throws IOException, SAXException, XMLStreamException {
		Depth depth = Depth.fromHeader(req.getHeader("depth"));

		Resource resource;
		if (depth == Depth.EMPTY) {
			// Lightweight path for Depth: 0 PROPFIND on the address-book URL —
			// the dominant iOS-polling pattern. Falls through to the heavy
			// resolver for everything else (root, principal, sub-resources).
			Resource lightweight = resolveAddressBookCollectionLightweight(req);
			resource = lightweight != null ? lightweight : getResource(req);
		} else {
			resource = getResource(req);
		}
		if (resource == null) {
			handleNotFound(req, resp);
			return;
		}

		String etag = resource.getEtag();
		if (etag != null && EtagUtil.matchesIfNoneMatch(req.getHeader("If-None-Match"), etag)) {
			resp.setHeader("ETag", EtagUtil.quote(etag));
			resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return;
		}

		List<QName> properties = CardDavRequestParser.parsePropfindBody(req.getInputStream());

		if (LOG.isDebugEnabled()) {
			StringWriter out = new StringWriter();
			out.write(req.getMethod() + " " + req.getPathInfo() + " " + depth + ": " + properties);
			out.write('\n');
			DebugUtil.dumpHeaders(out, req);
			LOG.debug(out.toString());
		}

		RenderContext ctx = renderContextOf(req);
		beginMultiStatus(resp, etag);
		XMLStreamWriter writer = MultiStatusWriter.open(resp.getOutputStream());
		try {
			resource.propfind(ctx, null, writer, properties);
			if (depth != Depth.EMPTY) {
				for (Resource content : resource.list()) {
					content.propfind(ctx, resource, writer, properties);
				}
			}
		} finally {
			MultiStatusWriter.close(writer);
		}
	}

	/**
	 * Resolves the request to a {@link AddressBookCollectionResource} if it
	 * targets exactly the address-book URL ({@code /addresses/{user}/}) and
	 * the user is authenticated. Returns {@code null} for any other path,
	 * for sub-resources, or when authentication fails — caller falls through
	 * to {@link #getResource}.
	 */
	private AddressBookCollectionResource resolveAddressBookCollectionLightweight(HttpServletRequest req) {
		String resourcePath = req.getPathInfo();
		if (resourcePath == null || !resourcePath.startsWith(ADDRESSES_PATH)) {
			return null;
		}
		int endIdx = resourcePath.indexOf('/', ADDRESSES_PATH.length());
		if (endIdx < 0 || endIdx != resourcePath.length() - 1) {
			// Not exactly /addresses/{user}/ — could be a sub-resource (PUT/GET).
			return null;
		}
		String principal = resourcePath.substring(ADDRESSES_PATH.length(), endIdx);
		if (!isAuthenticated(req, principal, resourcePath)) {
			return null;
		}
		UserSettings settings = LoginFilter.getUserSettings(req);
		String etag = AddressBookCache.getInstance().lookupCollectionEtag(principal, settings);
		String rootUrl = SERVER_LOC + req.getContextPath() + req.getServletPath();
		return new AddressBookCollectionResource(rootUrl, resourcePath, etag);
	}

	private void doReport(HttpServletRequest req, HttpServletResponse resp) throws IOException, SAXException, XMLStreamException {
		Resource resource = getResource(req);
		if (resource == null) {
			handleNotFound(req, resp);
			return;
		}

		String collectionEtag = resource.getEtag();
		if (collectionEtag != null && EtagUtil.matchesIfNoneMatch(req.getHeader("If-None-Match"), collectionEtag)) {
			resp.setHeader("ETag", EtagUtil.quote(collectionEtag));
			resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return;
		}

		MultiGetRequest report = CardDavRequestParser.parseMultiGetBody(req.getInputStream());
		if (report == null) {
			LOG.warn("Not implemented: " + DebugUtil.dumpRequestFull(req));
			resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
			return;
		}

		if (!(resource instanceof AddressBookResource addressBook)) {
			LOG.warn("addressbook-multiget on non-address-book resource: " + req.getPathInfo());
			resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
			return;
		}

		if (LOG.isDebugEnabled()) {
			StringWriter out = new StringWriter();
			out.write(req.getMethod() + " " + req.getPathInfo() + ": " + report.properties());
			out.write('\n');
			DebugUtil.dumpHeaders(out, req);
			LOG.debug(out.toString());
		}

		RenderContext ctx = renderContextOf(req);
		beginMultiStatus(resp, collectionEtag);
		XMLStreamWriter writer = MultiStatusWriter.open(resp.getOutputStream());
		try {
			addressBook.renderMultiGet(ctx, writer, report.hrefs(), report.properties());
		} finally {
			MultiStatusWriter.close(writer);
		}
	}

	private static RenderContext renderContextOf(HttpServletRequest req) {
		return new RenderContext(LoginFilter.getAuthenticatedUser(req));
	}

	private static void beginMultiStatus(HttpServletResponse resp, String etag) {
		if (etag != null) {
			resp.setHeader("ETag", EtagUtil.quote(etag));
		}
		resp.setStatus(SC_MULTI_STATUS);
		resp.setCharacterEncoding("utf-8");
		resp.setContentType("application/xml");
	}

	private void handleNotFound(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		LOG.warn("Not found: " + DebugUtil.dumpRequestFull(req));

		resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
	}

	private Resource getResource(HttpServletRequest req) {
		String serverRoot = req.getContextPath() + req.getServletPath();

		String rootUrl = CardDavServlet.SERVER_LOC + serverRoot;
		String resourcePath = req.getPathInfo();

		while (true) {
			if ("/".equals(resourcePath)) {
				return new RootResource(rootUrl, resourcePath);
			}

			else if (resourcePath.startsWith(PRINCIPALS_PATH)) {
				if (resourcePath.endsWith("/")) {
					// Note: dataaccessd/1.0 on iOS adds a slash to the principal resource.
					resourcePath = resourcePath.substring(0, resourcePath.length() - 1);
				}

				String principal = resourcePath.substring(PRINCIPALS_PATH.length());
				if (!isAuthenticated(req, principal, resourcePath)) {
					return null;
				}

				LOG.info("Starting synchronization for: " + principal);
				String userAgent = req.getHeader("User-Agent");
				UserSettings cachedSettings = LoginFilter.getUserSettings(req);
				DBService.getInstance().updateLastAccess(principal, System.currentTimeMillis(), userAgent, cachedSettings);
				return new PrincipalResource(rootUrl, resourcePath, principal);
			}

			else if (resourcePath.startsWith(ADDRESSES_PATH)) {
				int endIdx = resourcePath.indexOf('/', ADDRESSES_PATH.length());
				if (endIdx < 0) {
					LOG.warn("No principal found in address path: " + resourcePath);
					return null;
				}

				String principal = resourcePath.substring(ADDRESSES_PATH.length(), endIdx);
				if (!isAuthenticated(req, principal, resourcePath)) {
					return null;
				}

				UserSettings cachedSettings = LoginFilter.getUserSettings(req);
				AddressBookResource addressBook =
						AddressBookCache.getInstance().lookupAddressBook(rootUrl, serverRoot, resourcePath, principal, cachedSettings);

				if (endIdx < resourcePath.length() - 1) {
					return addressBook.lookupAddress(resourcePath.substring(endIdx + 1));
				} else {
					return addressBook;
				}
			} else {
				// This detects a common mistake when additional white space is appended to the CardDAV URL:
				Matcher matcher = WHITE_SPACE_PREFIX.matcher(resourcePath);
				if (matcher.lookingAt()) {
					resourcePath = resourcePath.substring(matcher.end() - 1);
					continue;
				}

				LOG.warn("Addressbook resource not found: " + resourcePath);
				return null;
			}
		}
	}

	private boolean isAuthenticated(HttpServletRequest req, String principal, String resourcePath) {
		String currentUser = LoginFilter.getAuthenticatedUser(req);
		if (currentUser == null) {
			LOG.warn("No authentication accessing: " + resourcePath);
			return false;
		}

		if (!principal.equals(currentUser)) {
			LOG.warn("Wrong user '" + principal + "' (expecting '" + currentUser + "') accessing: " + resourcePath);
			return false;
		}

		return true;
	}

}
