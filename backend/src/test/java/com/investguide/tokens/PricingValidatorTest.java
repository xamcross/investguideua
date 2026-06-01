package com.investguide.tokens;

import com.investguide.config.AppProperties;
import com.investguide.config.LlmProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * X7 DoD: seed-time pricing validation rejects under-priced / below-floor packs (SPECIFICATION
 * §9.1). Uses the shipping config defaults so the test also pins the canonical pack economics.
 */
class PricingValidatorTest {

    private static final double LLM_COST_USD = 0.0065;

    private PricingValidator validator(int safetyMultiple, int minPackUah, long fixedFee,
                                       double percentFee, double uahPerUsd) {
        AppProperties.Pricing pricing =
                new AppProperties.Pricing(safetyMultiple, minPackUah, fixedFee, percentFee, uahPerUsd);
        AppProperties app = new AppProperties(
                "http://localhost:4200",
                new AppProperties.Signup(5),
                new AppProperties.Rate(10, 100, 5),
                new AppProperties.Search(100_000_000L, 5),
                pricing,
                new AppProperties.Verification(86_400_000L),
                new AppProperties.Cors("http://localhost:4200"));
        LlmProperties llm = new LlmProperties(
                "test-key", "claude-haiku-4-5-20251001",
                3000, 700, 0.4, 40.0, 7000L, LLM_COST_USD, 1.0, 5.0);
        return new PricingValidator(app, llm);
    }

    private PricingValidator defaultValidator() {
        // Matches application.yml shipping defaults.
        return validator(10, 50, 0L, 0.0275, 44.5);
    }

    @Test
    void canonicalShippingPacksPassValidation() {
        List<TokenPack> active = TokenPackSeeder.canonicalPacks().stream()
                .filter(TokenPack::isActive)
                .toList();
        assertThatCode(() -> defaultValidator().validateActivePacks(active))
                .doesNotThrowAnyException();
    }

    @Test
    void underPricedPackAbortsWithDescriptiveError() {
        // 1 UAH for 5 tokens: net revenue is far below tokens x cost x safetyMultiple.
        TokenPack bad = new TokenPack("pack-bad", 5, 100L, "UAH", true);
        assertThatThrownBy(() -> defaultValidator().validateActivePacks(List.of(bad)))
                .isInstanceOf(PricingValidator.SeedPricingException.class)
                .hasMessageContaining("pack-bad")
                .hasMessageContaining("under-priced");
    }

    @Test
    void packBelowMinPackFloorAborts() {
        // 40 UAH (< 50 UAH floor) but tokens=1 so it clears the cost rule; only the floor fails.
        TokenPack belowFloor = new TokenPack("pack-cheap", 1, 4_000L, "UAH", true);
        assertThatThrownBy(() -> defaultValidator().validateActivePacks(List.of(belowFloor)))
                .isInstanceOf(PricingValidator.SeedPricingException.class)
                .hasMessageContaining("pack-cheap")
                .hasMessageContaining("floor");
    }

    @Test
    void emptyActiveSetIsAccepted() {
        assertThatCode(() -> defaultValidator().validateActivePacks(List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void inactivePacksAreNotPassedInSoOnlyActiveAreSeeded() {
        // Sanity: the seeder filters to active before calling the validator, so an under-priced
        // INACTIVE pack would never reach here. We assert canonical packs are all active.
        assertThat(TokenPackSeeder.canonicalPacks()).allMatch(TokenPack::isActive);
    }
}
