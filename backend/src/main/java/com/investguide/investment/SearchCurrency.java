package com.investguide.investment;

/**
 * Supported deposit/search currencies (SPECIFICATION §5.2 {@code currency}; ticket BE-S1).
 *
 * <p>Closed enum: an unknown value in a request body fails Jackson deserialization and is mapped to
 * a {@code 400 VALIDATION_ERROR} by the global handler. The same vocabulary is echoed back in the
 * §5.4 response and is compared (case-insensitively) against a provider's supported currencies when
 * pre-filtering the catalog (BE-C3) and when deciding whether the currency-risk disclaimer applies
 * (BE-S7).
 */
public enum SearchCurrency {
    UAH,
    USD
}
