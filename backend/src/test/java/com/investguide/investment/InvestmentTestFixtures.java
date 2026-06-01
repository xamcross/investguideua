package com.investguide.investment;

import com.investguide.catalog.Provider;
import com.investguide.catalog.ProviderCategory;
import com.investguide.catalog.ReturnRange;
import com.investguide.catalog.RiskLevel;
import com.investguide.config.AppProperties;
import com.investguide.config.LlmProperties;

import java.util.List;

/**
 * Shared builders for the BE-S unit tests — concrete config records and catalog providers so each test
 * stays focused on behaviour rather than wiring. No Spring context, no Mongo: these are plain values.
 */
final class InvestmentTestFixtures {

    private InvestmentTestFixtures() {
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

    static LlmProperties llmProperties(int maxInputTokens, double maxReturnPct) {
        return new LlmProperties(
                "test-key", "claude-haiku-4-5-20251001",
                maxInputTokens, 700, 0.4, maxReturnPct,
                7000L, 0.0065, 1.0, 5.0);
    }

    static Provider provider(String slug, ProviderCategory category, List<String> currencies,
                             long minAmount, ReturnRange typical, RiskLevel risk) {
        return new Provider(slug, slug + " Bank", category, "desc", minAmount, null,
                currencies, typical, risk, "https://" + slug + ".example/invest", true);
    }

    static Provider privatbank() {
        return provider("privatbank", ProviderCategory.BANK_DEPOSIT, List.of("UAH", "USD"),
                100_000L, new ReturnRange(13.0, 15.5), RiskLevel.LOW);
    }

    static Provider monobank() {
        return provider("monobank", ProviderCategory.BANK_DEPOSIT, List.of("UAH"),
                100_000L, new ReturnRange(12.0, 14.0), RiskLevel.LOW);
    }
}
