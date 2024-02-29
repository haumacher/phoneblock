package de.haumacher.phoneblock.ab;

import org.kohsuke.args4j.Option;

/**
 * Configuration options for {@link SipService}.
 */
public class SipServiceConfig {
	
	@Option(name = "--max-failures", usage = "Maximum number of consecutive registration failures before an answerbot is disabled.")
	public int maxFailures = 100;

}
