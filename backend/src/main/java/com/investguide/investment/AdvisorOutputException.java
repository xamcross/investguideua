package com.investguide.investment;

/**
 * Signals that the advisor's reply was unusable — not valid JSON for the §5.4 schema, or every option
 * referenced a provider outside the active catalog (SPECIFICATION §8.3, §8.5; ticket BE-S6).
 *
 * <p>Distinct from {@link AdvisorUnavailableException} (a transport/timeout fault): an output failure is
 * <em>retried once</em> with a corrective prompt by the orchestrator (BE-S3); only if the retry also
 * fails is it escalated to the §8.5 failure path (refund + {@code 502 ADVISOR_UNAVAILABLE}).
 */
public class AdvisorOutputException extends RuntimeException {

    public AdvisorOutputException(String message) {
        super(message);
    }
}
