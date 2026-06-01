package com.investguide.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * LLM / Anthropic Claude Haiku [CONFIG] (SPECIFICATION §2, §8.1, §8.2, §8.5).
 *
 * <p>{@code apiKey} is a SECRET — supplied via environment only, never committed, never sent
 * to the client. Startup fails fast if it is missing (see {@code @NotBlank}). Non-secret
 * defaults live in {@code application.yml}; values are not patched here so a legitimate
 * {@code 0.0} (e.g. {@code llm.temperature: 0.0} for full determinism) stays settable.
 */
@Validated
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        @NotBlank String apiKey,
        @NotBlank String model,
        @Min(1) int maxInputTokens,
        @Min(1) int maxOutputTokens,
        @DecimalMin("0.0") @DecimalMax("1.0") double temperature,
        @DecimalMin("0.0") double maxReturnPct,
        @Min(1) long requestTimeoutMs,
        @DecimalMin("0.0") double costPerSearchUsd,
        @DecimalMin("0.0") double inputUsdPerMillion,
        @DecimalMin("0.0") double outputUsdPerMillion
) {
}
