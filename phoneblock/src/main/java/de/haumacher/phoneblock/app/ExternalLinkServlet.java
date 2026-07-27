/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.jndi.JNDIProperties;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link HttpServlet} that redirects to external pages.
 *
 * <p>
 * The link targets are bundled with the web application in
 * {@code link.properties}, but every one of them can be re-pointed for a
 * concrete deployment, see {@link #applyOverrides(Properties)}. Therefore, a
 * link that goes stale (a sales offer that vanished, a moved documentation
 * page) can be fixed without building and deploying a new web application.
 * </p>
 */
@WebServlet(urlPatterns = ExternalLinkServlet.LINK_PREFIX + "*")
public class ExternalLinkServlet extends HttpServlet {

	/**
	 * Path prefix served by the {@link ExternalLinkServlet}.
	 */
	public static final String LINK_PREFIX = "/link/";

	/**
	 * Name of the JNDI context (and prefix of the corresponding system
	 * properties) holding link overrides, see
	 * {@link #applyOverrides(Properties)}.
	 */
	private static final String LINK_CONTEXT = "link";

	/**
	 * Name of the override pointing to a properties file, reserved and therefore
	 * not usable as a link name.
	 */
	private static final String FILE_PROPERTY = "file";

	private static final Logger LOG = LoggerFactory.getLogger(ExternalLinkServlet.class);

	private Properties _properties;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		_properties = new Properties();
		try (InputStream in = ExternalLinkServlet.class.getResourceAsStream("/link.properties")) {
			if (in == null) {
				LOG.error("No external link properties bundled with the application.");
			} else {
				_properties.load(in);
				LOG.info("Loaded {} external links.", _properties.size());
			}
		} catch (IOException ex) {
			LOG.error("Failed to load external link properties.", ex);
		}

		applyOverrides(_properties);
	}

	/**
	 * Overlays the links bundled with the web application with
	 * deployment-specific ones.
	 *
	 * <p>
	 * Two sources are applied, both optional and both looked up through
	 * {@link JNDIProperties} (a JNDI entry, or a system property of the same name
	 * with {@code /} written as {@code .}):
	 * </p>
	 *
	 * <ul>
	 * <li>{@code link/file}: Path of a properties file in the same format as the
	 * bundled {@code link.properties}. All of its entries are overlaid, so new
	 * links can be added as well.</li>
	 * <li>{@code link/<name>}: Target of a single link, e.g.
	 * {@code link/dongle-aliexpress}. Applied after the file, hence a single entry
	 * wins over the file's value for the same link.</li>
	 * </ul>
	 *
	 * <p>
	 * Overrides are read at startup only: after changing them, restart the
	 * application.
	 * </p>
	 */
	static void applyOverrides(Properties links) {
		Properties overrides;
		try {
			overrides = new JNDIProperties().lookupProperties(LINK_CONTEXT);
		} catch (NamingException ex) {
			// Outside a servlet container (tests, embedded server) there is no
			// environment context, but the system properties still apply.
			LOG.info("No JNDI context, taking link overrides from system properties: {}", ex.getMessage());
			overrides = systemProperties(LINK_CONTEXT + ".");
		}

		String fileName = (String) overrides.remove(FILE_PROPERTY);
		if (fileName != null && !fileName.isBlank()) {
			File file = new File(fileName);
			Properties external = new Properties();
			try (InputStream in = new FileInputStream(file)) {
				external.load(in);
				LOG.info("Overriding {} external links from '{}'.", external.size(), file);
			} catch (IOException ex) {
				LOG.error("Failed to load external links from '" + file + "'.", ex);
			}
			for (String name : external.stringPropertyNames()) {
				links.setProperty(name, external.getProperty(name));
			}
		}

		for (String name : overrides.stringPropertyNames()) {
			String target = overrides.getProperty(name);
			links.setProperty(name, target);
			LOG.info("Overrode external link '{}': {}", name, target);
		}
	}

	/**
	 * All system properties starting with the given prefix, with the prefix
	 * stripped from their names.
	 */
	private static Properties systemProperties(String prefix) {
		Properties result = new Properties();
		Properties systemProperties = System.getProperties();
		for (String name : systemProperties.stringPropertyNames()) {
			if (name.startsWith(prefix)) {
				result.setProperty(name.substring(prefix.length()), systemProperties.getProperty(name));
			}
		}
		return result;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || !pathInfo.startsWith("/")) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		int nameEnd = pathInfo.indexOf('/', 1);
		String suffix;
		if (nameEnd < 0) {
			nameEnd = pathInfo.length();
			suffix = "";
		} else {
			suffix = pathInfo.substring(nameEnd + 1);
		}
		
		String linkName = pathInfo.substring(1, nameEnd);
		String link = _properties.getProperty(linkName);
		if (link == null) {
			LOG.warn("Unknown link requested: " + linkName);
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		resp.sendRedirect(link.replace("{0}", suffix));
	}

}
