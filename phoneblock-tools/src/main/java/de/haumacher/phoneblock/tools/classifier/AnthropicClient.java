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
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;

/**
 * Thin wrapper around the official Anthropic Java SDK. Provides one typed and
 * one plain-text completion, both with a cacheable system prompt.
 */
public class AnthropicClient implements AutoCloseable {

	private final com.anthropic.client.AnthropicClient _client;
	private final String _model;

	public AnthropicClient(String apiKey, String model) {
		_client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
		_model = model;
	}

	/**
	 * Sends a completion request and parses the response against {@code schema}
	 * using the structured-outputs feature. The model is forced to emit JSON that
	 * matches the class's shape — no hand-rolled JSON parsing needed.
	 */
	public <T> T completeStructured(String cacheableSystemPrompt, String userMessage,
			long maxTokens, Class<T> schema) {
		StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
				.model(_model)
				.maxTokens(maxTokens)
				.systemOfTextBlockParams(List.of(cacheableSystem(cacheableSystemPrompt)))
				.outputConfig(schema)
				.addUserMessage(userMessage)
				.build();

		StructuredMessage<T> response = _client.messages().create(params);
		return response.content().stream()
				.flatMap(cb -> cb.text().stream())
				.map(tb -> tb.text())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No text block in structured response."));
	}

	/**
	 * Plain-text completion used for the final summary, where we just want prose
	 * back.
	 */
	public String complete(String cacheableSystemPrompt, String userMessage, long maxTokens) {
		MessageCreateParams params = MessageCreateParams.builder()
				.model(_model)
				.maxTokens(maxTokens)
				.systemOfTextBlockParams(List.of(cacheableSystem(cacheableSystemPrompt)))
				.addUserMessage(userMessage)
				.build();

		Message response = _client.messages().create(params);
		StringBuilder text = new StringBuilder();
		for (ContentBlock block : response.content()) {
			block.text().ifPresent(t -> text.append(t.text()));
		}
		return text.toString();
	}

	private static TextBlockParam cacheableSystem(String text) {
		return TextBlockParam.builder()
				.text(text)
				.cacheControl(CacheControlEphemeral.builder().build())
				.build();
	}

	@Override
	public void close() {
		_client.close();
	}
}
