package com.investguide.catalog.dto;

import com.investguide.catalog.Provider;
import com.investguide.catalog.ProviderCategory;
import com.investguide.catalog.ReturnRange;
import com.investguide.catalog.RiskLevel;

import java.util.List;

/**
 * Read-only provider view for the transparency UI (SPECIFICATION §5.1 {@code /providers};
 * tickets BE-C2, FE-ACCT2).
 *
 * <p>Mirrors the persisted {@link Provider} fields that are safe to expose so users can see the
 * bounded universe recommendations are drawn from. {@code id} is the stable slug. Money fields
 * stay in integer minor units (the client formats for display); no internal-only fields exist on
 * {@link Provider}, but using an explicit DTO keeps the wire contract decoupled from storage.
 */
public record ProviderResponse(
        String id,
        String name,
        ProviderCategory category,
        String description,
        long minAmount,
        Long maxAmount,
        List<String> currencies,
        ReturnRange typicalReturnPct,
        RiskLevel riskLevel,
        String sourceUrl
) {
    public static ProviderResponse from(Provider p) {
        return new ProviderResponse(
                p.getId(),
                p.getName(),
                p.getCategory(),
                p.getDescription(),
                p.getMinAmount(),
                p.getMaxAmount(),
                p.getCurrencies(),
                p.getTypicalReturnPct(),
                p.getRiskLevel(),
                p.getSourceUrl());
    }
}
