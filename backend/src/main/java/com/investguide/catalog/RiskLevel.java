package com.investguide.catalog;

/**
 * Risk level enum (SPECIFICATION §6 {@code providers.riskLevel}, §5.2 {@code riskTolerance},
 * §5.4 {@code riskLevel}; ticket BE-C1).
 *
 * <p>Shared vocabulary for a provider's inherent risk and (later, BE-S) the user's stated risk
 * tolerance. Persisted by name; the same three values appear in the search request/response.
 */
public enum RiskLevel {
    LOW,
    MODERATE,
    HIGH
}
