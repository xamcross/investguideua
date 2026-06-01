package com.investguide.investment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude Haiku implementation of {@link InvestmentAdvisorService} (SPECIFICATION §2, §8.1,
 * §8.2, §8.5; ticket BE-S5).
 *
 * <p>Uses the JDK's built-in {@link HttpClient} (Java 21) — no third-party SDK, nothing deprecated.
 * The Messages API is called with {@code max_tokens=llm.maxOutputTokens} and
 * {@code temperature<=llm.temperature} (the caps from §8.2), the API key sourced from config/secret
 * store only (never client-exposed, never logged). An explicit per-request timeout
 * {@code llm.requestTimeoutMs} bounds the call so a hung/slow upstream aborts and surfaces as an
 * {@link AdvisorUnavailableException} (BE-S3 refunds on it) rather than leaving the search stuck
 * {@code pending} (§8.5, §11 p95 &lt; 8 s). {@code inputTokens}/{@code outputTokens} are read from the
 * response {@code usage} block for cost accounting (X6).
 */
@Service
public class AnthropicInvestmentAdvisorService implements InvestmentAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicInvestmentAdvisorService.class);

    /** Pinned Messages API version header (stable contract for the MVP). */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final LlmProperties llm;
    private final ObjectMapper objectMapper;
    private final String messagesUrl;
    private final HttpClient httpClient;

    public AnthropicInvestmentAdvisorService(
            LlmProperties llm,
            ObjectMapper objectMapper,
            @Value("${llm.base-url:https://api.anthropic.com}") String baseUrl) {
        this.llm = llm;
        this.objectMapper = objectMapper;
        this.messagesUrl = trimTrailingSlash(baseUrl) + "/v1/messages";
        // Connect timeout is a fraction of the overall budget; the per-request timeout (below) is the
        // hard bound that guarantees the call returns.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, llm.requestTimeoutMs() / 2)))
                .build();
    }

    @Override
    public AdvisorResult advise(String systemPrompt, String userPrompt) {
        String body = serializeRequest(systemPrompt, userPrompt);
        HttpRequest request = HttpRequest.newBuilder(URI.create(messagesUrl))
                .timeout(Duration.ofMillis(llm.requestTimeoutMs()))
                .header("content-type", "application/json")
                .header("x-api-key", llm.apiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AdvisorUnavailableException("Advisor call interrupted", ex);
        } catch (Exception ex) {
            // Timeout (HttpTimeoutException) or any transport failure -> unavailable (never log the key).
            throw new AdvisorUnavailableException("Advisor transport failure", ex);
        }

        if (response.statusCode() / 100 != 2) {
            log.warn("advisor_http_error status={} model={}", response.statusCode(), llm.model());
            throw new AdvisorUnavailableException("Advisor returned HTTP " + response.statusCode());
        }
        return parseResponse(response.body());
    }

    private String serializeRequest(String systemPrompt, String userPrompt) {
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", llm.model());
        payload.put("max_tokens", llm.maxOutputTokens());
        payload.put("temperature", llm.temperature());
        payload.put("system", systemPrompt);
        payload.put("messages", List.of(userMessage));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new AdvisorUnavailableException("Failed to serialize advisor request", ex);
        }
    }

    private AdvisorResult parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            StringBuilder text = new StringBuilder();
            JsonNode content = root.path("content");
            if (content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        text.append(block.path("text").asText());
                    }
                }
            }
            JsonNode usage = root.path("usage");
            int inputTokens = usage.path("input_tokens").asInt(0);
            int outputTokens = usage.path("output_tokens").asInt(0);
            return new AdvisorResult(text.toString(), inputTokens, outputTokens);
        } catch (Exception ex) {
            throw new AdvisorUnavailableException("Failed to parse advisor response", ex);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
