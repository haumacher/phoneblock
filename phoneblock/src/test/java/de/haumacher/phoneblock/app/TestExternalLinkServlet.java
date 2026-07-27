/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test for the link overrides of the {@link ExternalLinkServlet} that allow
 * re-pointing an external link without deploying a new web application.
 *
 * <p>
 * Outside a servlet container there is no JNDI environment context, therefore
 * the overrides are taken from the system properties here. In a deployment,
 * the very same names are looked up in JNDI first, see
 * {@code JNDI-CONFIGURATION.md}.
 * </p>
 */
public class TestExternalLinkServlet {

	private final List<String> _systemProperties = new ArrayList<>();

	@AfterEach
	public void tearDown() {
		_systemProperties.forEach(System::clearProperty);
	}

	@Test
	public void testBundledLinksContainDongleSources() throws IOException {
		Properties links = bundledLinks();

		// The dongle page links to these instead of the shop URL, so that a vanished
		// offer can be re-pointed in the deployment's configuration.
		assertTrue(links.getProperty("dongle-aliexpress").startsWith("https://"));
		assertTrue(links.getProperty("dongle-ebay").startsWith("https://"));
	}

	@Test
	public void testNoOverrides() throws IOException {
		Properties links = bundledLinks();
		String bundled = links.getProperty("dongle-ebay");

		ExternalLinkServlet.applyOverrides(links);

		assertEquals(bundled, links.getProperty("dongle-ebay"));
	}

	@Test
	public void testOverrideSingleLink() throws IOException {
		setProperty("link.dongle-ebay", "https://example.com/offer");

		Properties links = bundledLinks();
		String otherBundled = links.getProperty("dongle-aliexpress");
		ExternalLinkServlet.applyOverrides(links);

		assertEquals("https://example.com/offer", links.getProperty("dongle-ebay"));

		// Only the configured link is affected.
		assertEquals(otherBundled, links.getProperty("dongle-aliexpress"));
	}

	@Test
	public void testOverrideFromFile(@TempDir Path dir) throws IOException {
		Path file = writeLinks(dir, "dongle-ebay=https://example.com/offer\ndongle-other-shop=https://example.com/other-shop\n");
		setProperty("link.file", file.toString());

		Properties links = bundledLinks();
		ExternalLinkServlet.applyOverrides(links);

		assertEquals("https://example.com/offer", links.getProperty("dongle-ebay"));

		// A file may also add a link that is not bundled with the application.
		assertEquals("https://example.com/other-shop", links.getProperty("dongle-other-shop"));

		// The file name itself is not a link.
		assertNull(links.getProperty("file"));
	}

	@Test
	public void testSingleLinkWinsOverFile(@TempDir Path dir) throws IOException {
		Path file = writeLinks(dir, "dongle-ebay=https://example.com/from-file\n");
		setProperty("link.file", file.toString());
		setProperty("link.dongle-ebay", "https://example.com/from-property");

		Properties links = bundledLinks();
		ExternalLinkServlet.applyOverrides(links);

		assertEquals("https://example.com/from-property", links.getProperty("dongle-ebay"));
	}

	@Test
	public void testMissingFileKeepsBundledLinks(@TempDir Path dir) throws IOException {
		setProperty("link.file", dir.resolve("does-not-exist.properties").toString());

		Properties links = bundledLinks();
		String bundled = links.getProperty("dongle-ebay");
		ExternalLinkServlet.applyOverrides(links);

		assertEquals(bundled, links.getProperty("dongle-ebay"));
	}

	/**
	 * The links as bundled with the web application, loaded the same way the
	 * servlet does.
	 */
	private static Properties bundledLinks() throws IOException {
		Properties result = new Properties();
		try (InputStream in = ExternalLinkServlet.class.getResourceAsStream("/link.properties")) {
			result.load(in);
		}
		return result;
	}

	private static Path writeLinks(Path dir, String contents) throws IOException {
		Path file = dir.resolve("link.properties");
		try (Writer out = Files.newBufferedWriter(file)) {
			out.write(contents);
		}
		return file;
	}

	private void setProperty(String name, String value) {
		System.setProperty(name, value);
		_systemProperties.add(name);
	}

}
