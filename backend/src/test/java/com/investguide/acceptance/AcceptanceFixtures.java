package com.investguide.acceptance;

import com.investguide.catalog.Provider;
import com.investguide.catalog.ProviderCategory;
import com.investguide.catalog.ReturnRange;
import com.investguide.catalog.RiskLevel;
import com.investguide.config.AppProperties;
import com.investguide.config.LlmProperties;

import java.util.List;

/**
 * Concrete config + catalog values for the QA1 acceptance suite. Mirrors the package-private
 * {@code InvestmentTestFixtures} (which lives in the {@code investment} package and is not visible here)
 * so the acceptance tests can construct the real services with realistic, spec-aligned configuration
 * (§14: 5 free tokens, {@code search.maxOptions=5}, {@code llm.maxInputTokens=3000}, etc.).
 */
final class AcceptanceFixtures {

    private AcceptanceFixtures() {
    }

    static AppProperties appProperties() {
        return appProperties(100_000_000L, 5, 5);
    }

    static AppProperties appProperties(long maxAmount, int maxOptions, int searchPerMinute) {
        return new AppProperties(
                "http://localhost:4200",
                new AppProperties.Signup(5),
                new AppProperties.Rate(10, 100, searchPerMinute),
                new AppProperties.Search(maxAmount, maxOptions),
                new AppProperties.Pricing(10, 50, 0L, 0.0275, 44.5),
                new AppProperties.Verification(86_400_000L),
                new AppProperties.Cors("http://localhost:4200"));
    }

    static LlmProperties llmProperties() {
        return llmProperties(3000, 40.0);
    }

    static LlmProperties llmProperties(int maxInputTokens, double maxReturnPct) {
        return new LlmProperties(
                "test-key", "claude-haiku-4-5-20251001",
                maxInputTokens, 700, 0.4, maxReturnPct,
                7000L, 0.0065, 1.0, 5.0);
    }

    /** PrivatBank: supports both UAH and USD, min 100000 kopiykas (1000 UAH). Category CASH_CURRENCY
     *  (non-grounded) so the acceptance flows are not subject to bond/metals grounding (011/012); the
     *  acceptance ACs here test token ledger, catalog grounding, and disclaimers, not bond grounding. */
    static Provider privatbank() {
        return new Provider("privatbank", "PrivatBank", ProviderCategory.CASH_CURRENCY, "desc",
                100_000L, null, List.of("UAH", "USD"), new ReturnRange(13.0, 15.5), RiskLevel.LOW,
                "https://privatbank.ua/invest", true);
    }
}
