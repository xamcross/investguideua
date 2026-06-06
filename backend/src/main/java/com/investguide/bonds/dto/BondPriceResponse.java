package com.investguide.bonds.dto;

import com.investguide.bonds.BondPrice;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Read-only bond price view for the ADMIN endpoint (feature 009 {@code GET /api/v1/bond-prices}).
 *
 * <p>Mirrors {@link BondPrice}. Money fields stay in integer minor units (the client formats for
 * display); an explicit DTO keeps the wire contract decoupled from storage.
 */
public record BondPriceResponse(
        String isin,
        boolean military,
        String currency,
        LocalDate maturity,
        LocalDate quotationDate,
        long sellPriceMinor,
        long buyPriceMinor,
        double sellYield,
        double buyYield,
        Instant fetchedAt
) {
    public static BondPriceResponse from(BondPrice b) {
        return new BondPriceResponse(
                b.getIsin(),
                b.isMilitary(),
                b.getCurrency(),
                b.getMaturity(),
                b.getQuotationDate(),
                b.getSellPriceMinor(),
                b.getBuyPriceMinor(),
                b.getSellYield(),
                b.getBuyYield(),
                b.getFetchedAt());
    }
}
