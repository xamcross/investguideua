package com.investguide.catalog;

/**
 * Provider category enum (SPECIFICATION §6 {@code providers.category}; ticket BE-C1).
 *
 * <p>The closed set of instrument families the curated catalog may contain. Persisted by name
 * (Spring Data Mongo default enum mapping), so the stored string matches the constant exactly —
 * which is also the value echoed in the §5.4 search response {@code category} field and validated
 * server-side when enforcing catalog grounding (§8.3, BE-S6).
 */
public enum ProviderCategory {
    BANK_DEPOSIT,
    GOV_BOND,
    BROKER,
    FUND,
    OTHER
}
