package com.investguide.investment;

/**
 * Per-search LLM usage + computed cost (SPECIFICATION §6 {@code searchRequests.llmUsage}, §8.2, §11;
 * tickets BE-S2, BE-S5, X6).
 *
 * <p>{@code inputTokens}/{@code outputTokens} are captured from the Anthropic API response.
 * {@code costUsd} is computed with the X6 formula
 * {@code inputTokens/1e6 * inUsdPerMillion + outputTokens/1e6 * outUsdPerMillion} from the configured
 * per-million prices and persisted for cost monitoring. It is reference accounting, not money moved by
 * the app, so a floating-point USD value is acceptable here (unlike token-pack prices, which are
 * integer minor units).
 */
public record LlmUsage(int inputTokens, int outputTokens, double costUsd) {

    /** Compute {@code costUsd} from token counts and the configured per-million USD prices (X6). */
    public static LlmUsage of(int inputTokens, int outputTokens,
                              double inUsdPerMillion, double outUsdPerMillion) {
        double cost = inputTokens / 1_000_000.0 * inUsdPerMillion
                + outputTokens / 1_000_000.0 * outUsdPerMillion;
        return new LlmUsage(inputTokens, outputTokens, cost);
    }
}
