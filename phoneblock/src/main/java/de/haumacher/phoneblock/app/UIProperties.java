package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.oauth.PhoneBlockConfigFactory;

/**
 * Paths for dependencies included as web jars.
 */
public class UIProperties {

	private static final Logger LOG = LoggerFactory.getLogger(UIProperties.class);

	public final static String BULMA_PATH = getWebJarPath("org.webjars.npm", "bulma");
	public final static String BULMA_COLLAPSIBLE_PATH = getWebJarPath("org.webjars.npm", "github-com-creativebulma-bulma-collapsible");
	public final static String BULMA_CALENDAR_PATH = getWebJarPath("org.webjars.npm", "bulma-calendar");
	public final static String FA_PATH = getWebJarPath("org.webjars", "font-awesome");
	public final static String JQUERY_PATH = getWebJarPath("org.webjars", "jquery");
	public final static String CHARTJS_PATH = getWebJarPath("org.webjars", "chartjs");
	public final static String SWAGGER_PATH = getWebJarPath("org.webjars.npm", "swagger-ui-dist");
	public final static String FLAGS_PATH = getWebJarPath("org.webjars.npm", "flag-icons");

	/**
	 * {@link #APP_PROPERTIES} key of the base URL (protocol and host) the OAuth provider redirects to
	 * after login, defaults to <code>https://phoneblock.net</code>.
	 */
	public static final String OAUTH_CALLBACK_BASE = "oauth.callbackBase";

	/**
	 * Settings taken from the deployment at startup: the JNDI name (<code>java:comp/env/</code> with
	 * slashes, or a system property with dots) and the {@link #APP_PROPERTIES} key it populates.
	 * 
	 * @see #loadRuntimeSettings()
	 */
	private static final String[][] RUNTIME_SETTINGS = {
		{"oauth.google.id", "google.id", "false"},
		{"oauth.google.secret", "google.secret", "true"},
		{"oauth.callbackBase", OAUTH_CALLBACK_BASE, "false"},
		{"bank.receiver", "bank.receiver", "false"},
		{"bank.account", "bank.account", "false"},
		{"bank.bic", "bank.bic", "false"},
	};

	/**
	 * Build information and runtime settings.
	 * 
	 * <p>
	 * Contains the build timestamp and version from the build. The deployment-specific entries
	 * (OAuth credentials, donation bank account) are added by {@link #loadRuntimeSettings()} during
	 * application startup, so the same WAR serves every deployment.
	 * </p>
	 */
	public static final Properties APP_PROPERTIES;
	
	static {
    	APP_PROPERTIES = new Properties();
    	try {
			APP_PROPERTIES.load(PhoneBlockConfigFactory.class.getResourceAsStream("/phoneblock.properties"));
		} catch (IOException ex) {
			LOG.error("Failed to read configuration properties.", ex);
		}
	}

	/**
	 * Completes {@link #APP_PROPERTIES} with the settings of this deployment.
	 * 
	 * <p>
	 * Each setting is looked up in JNDI (<code>java:comp/env/oauth/google/id</code>) first and in the
	 * system properties (<code>-Doauth.google.id=...</code>) second. Missing settings leave the
	 * corresponding feature disabled (no Google login, no donation QR code), which the consumers
	 * report themselves.
	 * </p>
	 */
	public static void loadRuntimeSettings() {
		Context envCtx;
		try {
			envCtx = (Context) new InitialContext().lookup("java:comp/env");
		} catch (NamingException ex) {
			LOG.info("No JNDI environment, reading runtime settings from system properties only.");
			envCtx = null;
		}

		for (String[] setting : RUNTIME_SETTINGS) {
			String property = setting[0];
			String key = setting[1];
			boolean secret = Boolean.parseBoolean(setting[2]);

			String value = null;
			String source = null;
			if (envCtx != null) {
				try {
					Object found = envCtx.lookup(property.replace('.', '/'));
					if (found != null) {
						value = found.toString();
						source = "JNDI";
					}
				} catch (NamingException ex) {
					// Not configured in JNDI.
				}
			}
			if (value == null) {
				value = System.getProperty(property);
				source = "system properties";
			}

			if (value == null || value.isBlank()) {
				LOG.info("Runtime setting '{}' not configured.", property);
				continue;
			}
			APP_PROPERTIES.setProperty(key, value);
			LOG.info("Using '{}' from {}: {}", property, source, secret ? "***" : value);
		}
	}

	private static String getWebJarPath(String groupId, String artifactId) {
		return "/webjars/" + artifactId + "/" + getVersion(groupId, artifactId);
	}

	private static String getVersion(String groupId, String artifactId) {
		String version;
		try {
			Properties properties = new Properties();
			try (InputStream in = UIProperties.class.getResourceAsStream("/META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties")) {
				if (in != null) {
					properties.load(in);
				}
			}
			version = properties.getProperty("version");
			LOG.info("Using '" + artifactId + "' version: " + version);
		} catch (IOException e) {
			version = "unknown";
		}
		return version;
	}
}
