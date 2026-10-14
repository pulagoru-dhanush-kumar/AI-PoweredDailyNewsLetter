package com.dhanush.AI_powereddailynewsletter.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.dhanush.AI_powereddailynewsletter.llm.Summarizer;

@Service
public class GroqService implements Summarizer {

    private static final String SYSTEM_PROMPT = """
            You summarize AI news accurately in simple English. Do not invent facts.
            Respond ONLY with a JSON object with these string fields:
            "title", "summary" (2-3 sentences), "key_takeaway" (one sentence).
            """;

    private final RestClient restClient;
    private final String model;
    private final int maxInputChars;
    private final int maxOutputTokens;

    public GroqService(
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.max-input-chars:3000}") int maxInputChars,
            @Value("${llm.max-output-tokens:600}") int maxOutputTokens) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY environment variable is missing");
        }
        this.model = model;
        this.maxInputChars = maxInputChars;
        this.maxOutputTokens = maxOutputTokens;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public String summarize(String title, String text) {
        String body = text.length() > maxInputChars ? text.substring(0, maxInputChars) : text;
        return call(SYSTEM_PROMPT, "Title: " + title + "\n\nText: " + body, true);
    }

    /** Plain-text helper used by /test-ai. */
    public String summarize(String text) {
        return call("Summarize AI news accurately in simple English. Do not invent facts.", text, false);
    }

    private String call(String system, String user, boolean json) {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("model", model);
        request.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        request.put("max_tokens", maxOutputTokens);
        request.put("reasoning_effort", "low");
        if (json) {
            request.put("response_format", Map.of("type", "json_object"));
        }

        Map<?, ?> response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("Groq returned no completion");
        }
        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        if (!(message.get("content") instanceof String result) || result.isBlank()) {
            throw new IllegalStateException("Groq returned an empty response");
        }
        return result;
    }
}