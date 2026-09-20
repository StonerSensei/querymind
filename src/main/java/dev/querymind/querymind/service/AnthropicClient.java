package dev.querymind.querymind.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * A tiny client for Anthropic's Claude API (the /v1/messages endpoint).
 *
 * It is OPTIONAL: if no API key is set, isConfigured() returns false and we
 * simply don't call the API. If a call fails for any reason, complete() returns
 * null instead of throwing - the caller then falls back to something sensible.
 * This keeps the app working with or without a key.
 */
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    public AnthropicClient(
            @Value("${querymind.anthropic.api-key:}") String apiKey,
            @Value("${querymind.anthropic.model:claude-3-5-sonnet-latest}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .build();
    }

    // True only if an API key has been provided.
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // Send a prompt to Claude and return its text answer, or null if we can't.
    public String complete(String prompt) {
        if (!isConfigured()) {
            return null;
        }
        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "max_tokens", 1024,
                    "messages", List.of(Map.of("role", "user", "content", prompt)));

            Map<?, ?> response = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            return extractText(response);
        } catch (Exception e) {
            log.warn("Call to Anthropic API failed", e);
            return null;
        }
    }

    // The response looks like: { "content": [ { "type": "text", "text": "..." } ] }
    private String extractText(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object content = response.get("content");
        if (content instanceof List<?> parts && !parts.isEmpty()
                && parts.get(0) instanceof Map<?, ?> firstPart) {
            Object text = firstPart.get("text");
            return text != null ? text.toString() : null;
        }
        return null;
    }
}
