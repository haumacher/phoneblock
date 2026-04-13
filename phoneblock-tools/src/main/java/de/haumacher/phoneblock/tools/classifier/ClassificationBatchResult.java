/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.tools.classifier;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Structured-output schema returned by the classifier LLM call. One entry per
 * submitted comment, in the same order as the input.
 */
public record ClassificationBatchResult(
		@JsonPropertyDescription("One verdict per submitted comment. Must use the same IDs as the input and must not invent new ones.")
		List<Entry> entries) {

	public enum Verdict {
		/** Comment contains concrete info about the caller and matches its rating. */
		good,
		/** Comment is uninformative or contradicts its rating. */
		bad,
	}

	public record Entry(
			@JsonPropertyDescription("The id field from the corresponding input comment.")
			String id,
			@JsonPropertyDescription("Verdict: 'good' if the comment is informative AND consistent with its rating, otherwise 'bad'.")
			Verdict classification) {
	}
}
