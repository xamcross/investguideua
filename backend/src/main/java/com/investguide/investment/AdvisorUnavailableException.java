package com.investguide.investment;

/**
 * Signals that the advisor (LLM) could not produce a usable reply — timeout, transport error, or a
 * non-success API response (SPECIFICATION §8.5; ticket BE-S5).
 *
 * <p>Distinct from an <em>invalid output</em> (which is retried once by BE-S6): this is a hard
 * unavailability that the orchestrator maps to {@code 502 ADVISOR_UNAVAILABLE} after refunding the
 * token (BE-S3 step 8). Carries no provider/internal detail to the client.
 */
public class AdvisorUnavailableException extends RuntimeException {

    public AdvisorUnavailableException(String message) {
        super(message);
    }

    public AdvisorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
