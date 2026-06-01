package com.investguide.investment;

/**
 * Language the advisor should write its free-text output (instrument / liquidity / rationale) in
 * (i18n: LLM-output localization). Ukrainian is the product default.
 *
 * <p>Optional on the request: when absent the service defaults to {@link #UK} (see
 * {@link SearchInput#from}). An unknown value fails Jackson deserialization and surfaces as
 * {@code 400 VALIDATION_ERROR}. This steers only the natural-language fields; the response schema,
 * provider slugs and enums are unaffected.
 */
public enum SearchLanguage {
    UK,
    EN
}
