package com.investguide.investment.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * One page of history (SPECIFICATION §4.4, §5.1; ticket BE-S8).
 *
 * <p>A stable, explicit envelope around the page rather than serialising Spring's {@code Page} directly
 * (whose JSON shape is version-sensitive), so the FE-HIST contract does not drift across upgrades.
 */
public record HistoryPageResponse(
        List<HistoryItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static HistoryPageResponse from(Page<?> page, List<HistoryItemResponse> items) {
        return new HistoryPageResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
