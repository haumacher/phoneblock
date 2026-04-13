/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.tools.classifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command-line entry point for the comment classifier / summarizer tool.
 */
public class CommentClassifier {

	private static final Logger LOG = LoggerFactory.getLogger(CommentClassifier.class);

	public static void main(String[] args) {
		try {
			ClassifierConfig config = ClassifierConfig.load(args);
			if (config.getAnthropicApiKey() == null || config.getAnthropicApiKey().isBlank()) {
				LOG.error("Missing anthropic.api-key in config or --anthropic-key on command line.");
				System.exit(2);
			}

			try (ClassifierDB db = new ClassifierDB(config.getDbUrl(), config.getDbUser(), config.getDbPassword());
					AnthropicClient llm = new AnthropicClient(config.getAnthropicApiKey(), config.getAnthropicModel())) {
				new ClassifyAndSummarize(db, llm, config).run();
			}
		} catch (Throwable ex) {
			LOG.error("Classifier run failed.", ex);
			System.exit(1);
		}
	}
}
