/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.zoolu.util.ConfigFile;

/**
 * Probe: the optional blocking options of {@link CustomerConfig} parse from the command line and
 * from a <code>.phoneblock</code> file, and stay unset when absent (#570).
 */
class TestCustomerConfigParsing {

	@Test
	void absentOptionsStayUnset() throws CmdLineException {
		CustomerConfig config = parse("--registrar", "fritz.box");

		assertNull(config.getMinVotes());
		assertNull(config.getWildcard());
	}

	@Test
	void commandLineOptions() throws CmdLineException {
		CustomerConfig config = parse("--registrar", "fritz.box", "--min-votes", "2", "--wildcard", "yes");

		assertEquals(Integer.valueOf(2), config.getMinVotes());
		assertEquals(Boolean.TRUE, config.getWildcard());
	}

	@Test
	void configFileOptions() throws IOException, CmdLineException {
		File file = File.createTempFile("phoneblock", ".cfg");
		try {
			Files.writeString(file.toPath(), 
					"registrar=fritz.box\n" + 
					"min-votes = 10\n" + 
					"wildcard=no\n", StandardCharsets.UTF_8);
			Collection<String> args = new ConfigFile(file).toArguments();

			CustomerConfig config = new CustomerConfig();
			new CmdLineParser(config).parseArgument(args);

			assertEquals(Integer.valueOf(10), config.getMinVotes());
			assertEquals(Boolean.FALSE, config.getWildcard());
		} finally {
			file.delete();
		}
	}

	@Test
	void minVotesMustBePositive() {
		assertThrows(CmdLineException.class, () -> parse("--registrar", "fritz.box", "--min-votes", "0"));
	}

	private static CustomerConfig parse(String... args) throws CmdLineException {
		CustomerConfig config = new CustomerConfig();
		new CmdLineParser(config).parseArgument(args);
		return config;
	}
}
