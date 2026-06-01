package com.investguide.investment.dto;

import com.investguide.investment.SearchInput;
import com.investguide.investment.SearchRequest;

import java.time.Instant;
import java.util.List;

/**
 * One row in the paginated history list (SPECIFICATION §4.4, §5.1; ticket BE-S8).
 *
 * <p>A lightweight summary — the input, timestamp, status and option count — enough to render the list
 * and link to the full detail ({@code GET /investments/{id}}) without shipping every option twice.
 */
public record HistoryItemResponse(
        String id,
        Instant createdAt,
        String status,
        SearchInput input,
        int optionCount
) {
    public static HistoryItemResponse from(SearchRequest r) {
        List<?> options = r.getOptions();
        return new HistoryItemResponse(
                r.getId(),
                r.getCreatedAt(),
                r.getStatus(),
                r.getInput(),
                options == null ? 0 : options.size());
    }
}
