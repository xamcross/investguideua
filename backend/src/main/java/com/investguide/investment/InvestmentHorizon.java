package com.investguide.investment;

/**
 * Optional investment time horizon (SPECIFICATION §5.2 {@code horizon}; ticket BE-S1).
 *
 * <p>Optional on the request: when present it is passed to the model as a soft preference. An
 * unknown value fails deserialization and surfaces as {@code 400 VALIDATION_ERROR}.
 */
public enum InvestmentHorizon {
    SHORT,
    MEDIUM,
    LONG
}
