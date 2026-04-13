/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.tools.classifier;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Thin HTTP client for the Anthropic Messages API.
 * <p>
 * Uses prompt caching on the system prompt so the repeated classification
 * instruction does not get re-billed on every batch.
 * </p>
 */
public class AnthropicClient implements AutoCloseable {

	private static final String API_URL = "https://api.anthropic.com/v1/messages";
	private static final String ANTHROPIC_VERSION = "2023-06-01";

	private final HttpClient _http;
	private final ObjectMapper _json;
	private final String _apiKey;
	private final String _model;

	public AnthropicClient(String apiKey, String model) {
		_apiKey = apiKey;
		_model = model;
		_json = new ObjectMapper();
		_http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
	}

	/**
	 * Sends a message with a cacheable system prompt and returns the text content of
	 * the first completion. The system prompt is marked with
	 * {@code cache_control: ephemeral} so Anthropic caches it across batches.
	 */
	public String complete(String cacheableSystemPrompt, String userMessage, int maxTokens) throws IOException, InterruptedException {
		ObjectNode root = _json.createObjectNode();
		root.put("model", _model);
		root.put("max_tokens", maxTokens);

		ArrayNode systemArr = root.putArray("system");
		ObjectNode sys = systemArr.addObject();
		sys.put("type", "text");
		sys.put("text", cacheableSystemPrompt);
		sys.putObject("cache_control").put("type", "ephemeral");

		ArrayNode messages = root.putArray("messages");
		ObjectNode msg = messages.addObject();
		msg.put("role", "user");
		msg.put("content", userMessage);

		HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
				.timeout(Duration.ofMinutes(2))
				.header("x-api-key", _apiKey)
				.header("anthropic-version", ANTHROPIC_VERSION)
				.header("content-type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(_json.writeValueAsString(root)))
				.build();

		HttpResponse<String> response = _http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() / 100 != 2) {
			throw new IOException("Anthropic API error " + response.statusCode() + ": " + response.body());
		}

		JsonNode body = _json.readTree(response.body());
		JsonNode content = body.path("content");
		StringBuilder text = new StringBuilder();
		if (content.isArray()) {
			for (JsonNode part : content) {
				if ("text".equals(part.path("type").asText())) {
					text.append(part.path("text").asText());
				}
			}
		}
		return text.toString();
	}

	@Override
	public void close() {
		// HttpClient has no explicit close in JDK 17.
	}
}
