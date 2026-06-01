package com.investguide.investment;

import com.investguide.catalog.RiskLevel;
import com.investguide.investment.dto.SearchRequestDto;

/**
 * Immutable snapshot of the validated search input persisted on a {@link SearchRequest}
 * (SPECIFICATION §6 {@code searchRequests.input}; ticket BE-S2).
 *
 * <p>Stored exactly as accepted (after validation and goal length-capping) so history (BE-S8) can show
 * what the user asked. {@code amount} is integer minor units (kopiykas), consistent with the request.
 *
 * <p>{@code language} is the resolved (never-null) output language for the advisor's free text; it
 * defaults to {@link SearchLanguage#UK} when the request omits it (i18n: LLM-output localization).
 */
public record SearchInput(
        long amount,
        SearchCurrency currency,
        InvestmentHorizon horizon,
        RiskLevel riskTolerance,
        String goals,
        SearchLanguage language
) {
    public static SearchInput from(SearchRequestDto dto) {
        SearchLanguage language = dto.language() != null ? dto.language() : SearchLanguage.UK;
        return new SearchInput(dto.amount(), dto.currency(), dto.horizon(), dto.riskTolerance(),
                dto.goals(), language);
    }
}
