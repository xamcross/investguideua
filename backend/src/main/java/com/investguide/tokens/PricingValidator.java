package com.investguide.tokens;

import com.investguide.config.AppProperties;
import com.investguide.config.LlmProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seed-time pricing validation (SPECIFICATION §9.1, §14; ticket X7).
 *
 * <p>Enforces the unit-economics rule that keeps every active token pack profitable after
 * gateway fees and well above LLM cost. For each active pack:
 *
 * <pre>{@code  price - (price * gatewayPercentFee + gatewayFixedFee)  >  tokens * llmCostPerSearch * safetyMultiple }</pre>
 *
 * and the smallest active pack price must be at least {@code pricing.minPackUah}. A seed that
 * violates either condition aborts startup (the seeder rethrows {@link SeedPricingException}).
 *
 * <p><b>Currency normalisation:</b> pack prices are UAH minor units (kopiykas) while the LLM cost
 * is configured in USD ({@code llm.costPerSearchUsd}). The cost is converted to kopiykas via
 * {@code pricing.uahPerUsd} so both sides of the inequality are kopiykas. The arithmetic uses
 * {@code double} deliberately: these are <i>ratio/threshold computations</i> (percentages, FX),
 * not stored or transacted money — every persisted monetary value remains an integer minor unit.
 */
@Component
public class PricingValidator {

    private final AppProperties.Pricing pricing;
    private final double llmCostPerSearchUsd;

    public PricingValidator(AppProperties appProperties, LlmProperties llmProperties) {
        this.pricing = appProperties.pricing();
        this.llmCostPerSearchUsd = llmProperties.costPerSearchUsd();
    }

    /**
     * Validate the set of <b>active</b> packs about to be seeded.
     *
     * @throws SeedPricingException if any active pack is under-priced relative to the §9.1 rule,
     *                              or the smallest active pack is below the {@code minPackUah} floor
     */
    public void validateActivePacks(List<TokenPack> activePacks) {
        if (activePacks == null || activePacks.isEmpty()) {
            return; // nothing to validate (no purchasable packs)
        }

        double llmCostPerSearchKopiykas = llmCostPerSearchUsd * pricing.uahPerUsd() * 100.0;

        for (TokenPack pack : activePacks) {
            double price = pack.getPriceMinorUnits();
            double fees = price * pricing.gatewayPercentFee() + pricing.gatewayFixedFeeMinorUnits();
            double netRevenue = price - fees;
            double costFloor = (double) pack.getTokens() * llmCostPerSearchKopiykas * pricing.safetyMultiple();

            if (!(netRevenue > costFloor)) {
                throw new SeedPricingException(String.format(
                        "Token pack '%s' is under-priced: net revenue after fees = %.2f kopiykas, "
                                + "but must exceed tokens(%d) x llmCostPerSearch(%.4f kopiykas) "
                                + "x safetyMultiple(%d) = %.2f kopiykas. "
                                + "Raise priceMinorUnits or lower the token count.",
                        pack.getId(), netRevenue, pack.getTokens(),
                        llmCostPerSearchKopiykas, pricing.safetyMultiple(), costFloor));
            }
        }

        long minPackFloorKopiykas = pricing.minPackUah() * 100L;
        TokenPack smallest = activePacks.stream()
                .min((a, b) -> Long.compare(a.getPriceMinorUnits(), b.getPriceMinorUnits()))
                .orElseThrow();
        if (smallest.getPriceMinorUnits() < minPackFloorKopiykas) {
            throw new SeedPricingException(String.format(
                    "Smallest active pack '%s' priced at %d kopiykas is below the floor of %d kopiykas "
                            + "(pricing.minPackUah = %d UAH).",
                    smallest.getId(), smallest.getPriceMinorUnits(),
                    minPackFloorKopiykas, pricing.minPackUah()));
        }
    }

    /** Thrown on an invalid seed so startup aborts with a clear, actionable message. */
    public static class SeedPricingException extends IllegalStateException {
        public SeedPricingException(String message) {
            super(message);
        }
    }
}
