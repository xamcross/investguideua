package com.investguide.investment;

/**
 * The raw outcome of one advisor call (ticket BE-S5).
 *
 * <p>{@code text} is the model's reply (expected to be strict JSON per the prompt, validated downstream
 * by BE-S6). {@code inputTokens}/{@code outputTokens} are captured from the API {@code usage} block and
 * fed into the X6 cost computation ({@link LlmUsage#of}).
 */
public record AdvisorResult(String text, int inputTokens, int outputTokens) {
}
