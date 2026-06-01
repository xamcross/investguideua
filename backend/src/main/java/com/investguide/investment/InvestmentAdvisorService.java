package com.investguide.investment;

/**
 * Server-side-only abstraction over the LLM that produces investment options (SPECIFICATION §2, §8.1;
 * ticket BE-S5).
 *
 * <p>An interface (not a concrete class) so the provider can be swapped without touching business
 * logic (§2): the orchestrator (BE-S3) depends only on this type, the Anthropic implementation is
 * injected in production, and a deterministic fake is injected in tests. The Anthropic API key lives in
 * server config only and never reaches the client (§8.1).
 */
public interface InvestmentAdvisorService {

    /**
     * Run one advisor completion within the configured token budget and timeout.
     *
     * @param systemPrompt the guardrail system prompt (BE-S4)
     * @param userPrompt   the request + allowed-catalog user prompt (BE-S4)
     * @return the raw model text plus captured token usage
     * @throws AdvisorUnavailableException on timeout, transport error, or a non-success API response —
     *         a signal the orchestrator (BE-S3 step 8) treats per §8.5 (refund the token, mark the
     *         request {@code failed}, return {@code 502 ADVISOR_UNAVAILABLE}); the call must never block
     *         indefinitely and leave the request stuck {@code pending}.
     */
    AdvisorResult advise(String systemPrompt, String userPrompt);
}
