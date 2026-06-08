package com.investguide.investment;

import com.investguide.catalog.ProviderCategory;
import com.investguide.catalog.ReturnRange;
import com.investguide.catalog.RiskLevel;

/**
 * A single recommended investment option (SPECIFICATION §5.4 {@code options[]}; tickets BE-S6, BE-S2).
 *
 * <p>Used both as the persisted shape (embedded in {@link SearchRequest#getOptions()}) and as the
 * wire shape in the search response, so history detail renders identically to a fresh search.
 *
 * <p><b>Provenance / anti-hallucination (§8.3, BE-S6):</b> only {@code instrument}, {@code liquidity},
 * {@code rationale}, {@code currency} and the (clamped) {@code expectedReturnPct} come from the model.
 * The identity and reference fields — {@code providerId}, {@code providerName}, {@code category},
 * {@code minAmount}, {@code sourceUrl} — are backfilled <em>server-side from the active catalog</em>
 * after the model's slug is validated, so a model can never invent a provider name, a category, or
 * (critically) a source URL. {@code minAmount} stays in integer minor units (kopiykas); the client
 * formats it for display.
 *
 * <p>{@code currency} is the currency the instrument is denominated in — constrained server-side to a
 * currency the provider actually supports, defaulting to the requested currency. It drives the
 * currency-risk disclaimer (BE-S7): if any option's {@code currency} differs from the request currency,
 * the additional disclaimer is appended.
 *
 * <p><b>Precious-metals grounding (feature 011):</b> {@code metal} and {@code metalPricePerGramMinor}
 * are set ONLY for a {@code PRECIOUS_METALS} option, server-side from stored PrivatBank quotes - the
 * model never supplies the price. {@code metal} is {@code GOLD}/{@code SILVER}; {@code
 * metalPricePerGramMinor} is the exact current bank sale rate in integer minor units (kopiykas) per
 * gram. Both are {@code null} for every other option, and {@code null} for legacy persisted searches
 * (boxed types, so a missing stored field reads back null).
 *
 * <p><b>Bond grounding (feature 012):</b> {@code bondIsin} and {@code bondSellPriceMinor} are set ONLY
 * for a grounded {@code MILITARY_BOND}/{@code GOV_BOND} option, server-side from the stored feature-009
 * {@code bondPrices} quote for the model-named ISIN - the model never supplies the price. For such an
 * option {@code expectedReturnPct} is also set server-side to a degenerate range (min == max) equal to
 * the bond's stored sell yield. {@code bondSellPriceMinor} is the stored sell price in integer minor
 * units (kopiykas) per 1000 face value. Both are {@code null} for every other option and for legacy
 * persisted searches.
 */
public record InvestmentOption(
        String providerId,
        String providerName,
        String instrument,
        ProviderCategory category,
        SearchCurrency currency,
        ReturnRange expectedReturnPct,
        RiskLevel riskLevel,
        long minAmount,
        String liquidity,
        String rationale,
        String sourceUrl,
        String metal,
        Long metalPricePerGramMinor,
        String bondIsin,
        Long bondSellPriceMinor
) {
}
