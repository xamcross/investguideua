package com.investguide.metals.dto;

import com.investguide.metals.MetalPrice;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Read-only metal price view for the ADMIN endpoint (feature 011 {@code GET /api/v1/metal-prices}).
 *
 * <p>Mirrors {@link MetalPrice}. Money fields stay in integer minor units (kopiykas) per gram (the
 * client formats for display); an explicit DTO keeps the wire contract decoupled from storage.
 */
public record MetalPriceResponse(
        String id,
        String metal,
        String rateGroup,
        String weightKey,
        double weightGrams,
        String currency,
        long purchaseRateMinor,
        long saleRateMinor,
        LocalDate quotationDate,
        Instant fetchedAt
) {
    public static MetalPriceResponse from(MetalPrice m) {
        return new MetalPriceResponse(
                m.getId(),
                m.getMetal(),
                m.getRateGroup(),
                m.getWeightKey(),
                m.getWeightGrams(),
                m.getCurrency(),
                m.getPurchaseRateMinor(),
                m.getSaleRateMinor(),
                m.getQuotationDate(),
                m.getFetchedAt());
    }
}
