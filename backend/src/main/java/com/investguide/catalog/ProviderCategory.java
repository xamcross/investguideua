package com.investguide.catalog;

/**
 * Investment-instrument enum (SPECIFICATION §6 {@code providers.category}; ticket BE-C1).
 *
 * <p>The closed set of instrument types the curated catalog may contain — the thirteen instrument
 * families available to Ukrainian retail investors (war bonds, OVDP, cash FX, metals, real estate,
 * index ETFs, foreign stocks, crypto, corporate bonds, crowdlending, pension funds, life insurance
 * and startup/business equity). Persisted by name (Spring Data Mongo default enum mapping), so the
 * stored string matches the constant exactly — which is also the value echoed in the §5.4 search
 * response {@code category} field and validated server-side when enforcing catalog grounding
 * (§8.3, BE-S6). The {@code /providers} transparency screen groups the catalog by this value.
 */
public enum ProviderCategory {
    MILITARY_BOND,
    GOV_BOND,
    CASH_CURRENCY,
    PRECIOUS_METALS,
    REAL_ESTATE,
    INDEX_ETF,
    FOREIGN_STOCKS,
    CRYPTO,
    CORPORATE_BOND,
    CROWDLENDING,
    PENSION_FUND,
    LIFE_INSURANCE,
    BUSINESS_EQUITY
}
