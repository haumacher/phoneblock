package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static String getWebJarPath(String groupId, String artifactId) {
		return "/webjars/" + artifactId + "/" + getVersion(groupId, artifactId);
	}

	private static String getVersion(String groupId, String artifactId) {
		String version;
		try {
			Properties properties = new Properties();
			properties.load(UIProperties.class.getResourceAsStream("/META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties"));
			version = properties.getProperty("version");
			LOG.info("Using '" + artifactId + "' version: " + version);
		} catch (IOException e) {
			version = "unknown";
		}
		return version;
	}
}
