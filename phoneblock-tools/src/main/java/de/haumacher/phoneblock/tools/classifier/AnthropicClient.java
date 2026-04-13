/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.tools.classifier;

import java.util.List;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;

/**
 * Thin wrapper around the official Anthropic Java SDK that exposes the single
 * prompt-cached completion call used by the classifier.
 */
public class AnthropicClient implements AutoCloseable {

	private final com.anthropic.client.AnthropicClient _client;
	private final String _model;

	public AnthropicClient(String apiKey, String model) {
		_client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
		_model = model;
	}

	/**
	 * Sends a completion request whose system prompt is marked as ephemerally
	 * cacheable. Returns the concatenated text of all text blocks in the response.
	 */
	public String complete(String cacheableSystemPrompt, String userMessage, long maxTokens) {
		TextBlockParam system = TextBlockParam.builder()
				.text(cacheableSystemPrompt)
				.cacheControl(CacheControlEphemeral.builder().build())
				.build();

		MessageCreateParams params = MessageCreateParams.builder()
				.model(_model)
				.maxTokens(maxTokens)
				.systemOfTextBlockParams(List.of(system))
				.addUserMessage(userMessage)
				.build();

		Message response = _client.messages().create(params);
		StringBuilder text = new StringBuilder();
		for (ContentBlock block : response.content()) {
			block.text().ifPresent(t -> text.append(t.text()));
		}
		return text.toString();
	}

	@Override
	public void close() {
		_client.close();
	}
}
