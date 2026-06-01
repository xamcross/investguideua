package com.investguide.investment;

import com.investguide.catalog.RiskLevel;
import com.investguide.investment.dto.SearchRequestDto;

/**
 * Immutable snapshot of the validated search input persisted on a {@link SearchRequest}
 * (SPECIFICATION §6 {@code searchRequests.input}; ticket BE-S2).
 *
 * <p>Stored exactly as accepted (after validation and goal length-capping) so history (BE-S8) can show
 * what the user asked. {@code amount} is integer minor units (kopiykas), consistent with the request.
 */
public record SearchInput(
        long amount,
        SearchCurrency currency,
        InvestmentHorizon horizon,
        RiskLevel riskTolerance,
        String goals
) {
    public static SearchInput from(SearchRequestDto dto) {
        return new SearchInput(dto.amount(), dto.currency(), dto.horizon(), dto.riskTolerance(),
                dto.goals());
    }
}
