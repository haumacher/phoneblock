package de.haumacher.phoneblock.ab;

import org.kohsuke.args4j.Option;

/**
 * Configuration options for {@link SipService}.
 */
public class SipServiceConfig {
	
	@Option(name = "--disable-timeout", usage = "Time in milliseconds after which a registered bot gets automatically disabled, if no more registration is possible.")
	public long disableTimeout = 3 * 24 * 60 * 60 * 1000L;

}
