/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberBlock;
import de.haumacher.phoneblock.carddav.schema.DavSchema;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link Resource} representing a collection of {@link AddressBookResource}s.
 */
public class AddressBookResource extends AbstractAddressBook {

	private static final Logger LOG = LoggerFactory.getLogger(AddressBookResource.class);

	private final String _principal;
	private String _serverRoot;

	private final Map<String, AddressResource> _addressById;

	private final String _etag;

	/**
	 * Creates a {@link AddressBookResource}.
	 *
	 * @param rootUrl
	 *        The full URL (including protocol) of the CardDAV servlet.
	 * @param serverRoot
	 *        The absolute path of the CardDAV servlet relative to the server.
	 * @param numbers
	 *        Blocks visible to the user. May be the common list alone, the common list
	 *        with personal singletons appended, or the result of a full per-user pipeline —
	 *        from this resource's point of view they are all just blocks.
	 * @param etag
	 *        The collection ETag, precomputed from the user's blocks, personal
	 *        singletons and settings. See {@link CollectionEtag}.
	 */
	public AddressBookResource(String rootUrl, String serverRoot, String resourcePath, String principal,
			List<NumberBlock> numbers, String etag) {
		this(rootUrl, serverRoot, resourcePath, principal, numbers, etag, block -> null);
	}

	/**
	 * Creates a {@link AddressBookResource} whose blocks may use shared pre-rendered fragments.
	 *
	 * @param fragments
	 *        Lookup of the shared {@link BlockFragments} for a block, returning {@code null}
	 *        for blocks that are rendered per request.
	 */
	AddressBookResource(String rootUrl, String serverRoot, String resourcePath, String principal,
			List<NumberBlock> numbers, String etag, Function<NumberBlock, BlockFragments> fragments) {
		super(rootUrl, resourcePath);
		_serverRoot = serverRoot;
		_principal = principal;
		_etag = etag;

		_addressById = numbers
			.stream()
			.map(r -> newAddressResource(r, r.getName(), fragments.apply(r)))
			.collect(Collectors.toMap(AddressResource::getId, r -> r));
	}

	private AddressResource newAddressResource(NumberBlock block, String id, BlockFragments fragments) {
		return new AddressResource(block, getRootUrl(), getResourcePath() + id, _principal, fragments);
	}

	@Override
	public Collection<? extends Resource> list() {
		return _addressById.values();
	}

	@Override
	public Resource get(String url) {
		String rootUrl = getRootUrl();

		int prefixLength;
		if (url.startsWith(rootUrl)) {
			prefixLength = rootUrl.length();
		} else {
			if (url.startsWith("/")) {
				if (url.startsWith(_serverRoot)) {
					prefixLength = _serverRoot.length();
				} else {
					// Invalid URL.
					LOG.warn("Received invalid absolute contact URL outside service '" + rootUrl + "': " + url);
					return null;
				}
			} else if (url.indexOf(':') >= 0) {
				// Invalid URL.
				LOG.warn("Received invalid contact URL outside server '" + rootUrl + "': " + url);
				return null;
			} else {
				return lookupAddress(url);
			}
		}
		if (!url.startsWith(getResourcePath(), prefixLength)) {
			LOG.warn("Received invalid contact URL outside address book '" + getResourcePath() + "': " + url);
			return null;
		}

		return lookupAddress(url.substring(prefixLength + getResourcePath().length()));
	}

	public Resource lookupAddress(String id) {
		// Relative URL.
		AddressResource result = _addressById.get(id);
		if (result == null) {
			// Maybe this is a put operation, create an empty entry.
			return newAddressResource(new NumberBlock(id, java.util.List.of(id)), id, null);
		}
		return result;
	}

	@Override
	public String getEtag() {
		return _etag;
	}

	/**
	 * Renders the multistatus body of a {@code <c:addressbook-multiget>} REPORT
	 * request: one {@code <d:response>} per addressed href, with the requested
	 * properties of the resolved child resource (or 404 if it does not exist).
	 */
	public void renderMultiGet(RenderContext ctx, MultiStatusWriter out,
			List<String> hrefs, List<QName> properties) throws XMLStreamException, IOException {
		XMLStreamWriter writer = out.xml();
		for (String url : hrefs) {
			writer.writeStartElement(DavSchema.DAV_NS, "response");
			writer.writeStartElement(DavSchema.DAV_NS, "href");
			writer.writeCharacters(url);
			writer.writeEndElement();

			Resource content = get(url);
			if (content != null) {
				content.writeMultiGetPropstat(ctx, out, properties);
			} else {
				writer.writeStartElement(DavSchema.DAV_NS, "propstat");
				writeStatus(writer, HttpServletResponse.SC_NOT_FOUND, "Not Found");
				writer.writeEndElement();
			}
			writer.writeEndElement();
		}
	}

	private static void writeStatus(XMLStreamWriter writer, int code, String reason)
			throws XMLStreamException {
		writer.writeStartElement(DavSchema.DAV_NS, "status");
		writer.writeCharacters("HTTP/1.1 " + code + " " + reason);
		writer.writeEndElement();
	}
}
