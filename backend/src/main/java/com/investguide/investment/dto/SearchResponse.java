package com.investguide.investment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.investguide.investment.InvestmentOption;
import com.investguide.investment.SearchCurrency;
import com.investguide.investment.SearchRequest;

import java.util.List;

/**
 * Search result payload (SPECIFICATION §5.4; tickets BE-S3, BE-S7, BE-S8).
 *
 * <p>Returned both by a fresh {@code POST /investments/search} and by {@code GET /investments/{id}}
 * (history detail), so the client renders the two identically. {@code requestId} is the persisted
 * {@link SearchRequest} id. {@code tokenBalance} is the caller's live balance after the (successful)
 * debit. {@code disclaimer} is the mandatory, server-controlled financial disclaimer present on every
 * result set (§1.1, AC #5); {@code currencyRiskDisclaimer} is added only when at least one option's
 * currency differs from the requested deposit currency (§10, BE-S7) and is otherwise {@code null}
 * (omitted from JSON).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(
        String requestId,
        int tokenBalance,
        long amount,
        SearchCurrency currency,
        List<InvestmentOption> options,
        String disclaimer,
        String currencyRiskDisclaimer
) {
}
