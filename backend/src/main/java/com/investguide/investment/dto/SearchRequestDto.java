package com.investguide.investment.dto;

import com.investguide.catalog.RiskLevel;
import com.investguide.investment.InvestmentHorizon;
import com.investguide.investment.SearchCurrency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Validated request body for {@code POST /investments/search} (SPECIFICATION §5.2; ticket BE-S1).
 *
 * <p>Bean-validation annotations enforce the structural rules; the global handler aggregates any
 * failures into a single {@code 400 VALIDATION_ERROR} body. Unknown JSON properties are rejected
 * globally ({@code spring.jackson.deserialization.fail-on-unknown-properties: true}) and also surface
 * as {@code VALIDATION_ERROR}, satisfying the BE-S1 "reject unknown fields" rule without per-DTO code.
 *
 * <p><b>Money units (project rule):</b> {@code amount} is an integer in <b>minor units (kopiykas)</b>
 * — never a float, never whole UAH. This matches {@link com.investguide.catalog.Provider} storage and
 * the {@code ProviderCatalogService.filterFor(long, String)} contract, so the value flows straight
 * into pre-prompt filtering without a unit conversion. The frontend collects a UAH figure and sends
 * {@code amount * 100}. The configured upper bound {@code search.maxAmount} is server-authoritative and
 * enforced in the service (it is a {@code [CONFIG]} value, so it is deliberately not hard-coded here);
 * the lower bound ({@code > 0}) is enforced by {@link Positive}.
 *
 * @param amount        requested amount in integer minor units (kopiykas); required, {@code > 0}
 * @param currency      required deposit currency ({@link SearchCurrency})
 * @param horizon       optional time horizon ({@link InvestmentHorizon})
 * @param riskTolerance optional risk tolerance ({@link RiskLevel})
 * @param goals         optional free text, max 280 chars; wrapped as data before prompting (§8.4)
 */
public record SearchRequestDto(
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than 0")
        Long amount,

        @NotNull(message = "currency is required")
        SearchCurrency currency,

        InvestmentHorizon horizon,

        RiskLevel riskTolerance,

        @Size(max = 280, message = "goals must be at most 280 characters")
        String goals
) {
}
