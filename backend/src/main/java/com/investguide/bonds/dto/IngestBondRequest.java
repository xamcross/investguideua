package com.investguide.bonds.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * One bond in an ingest batch (feature 009 {@code POST /api/v1/admin/bond-prices}).
 *
 * <p><b>All fields are boxed object types</b> (not primitives) so an omitted JSON field deserializes
 * to a rejectable {@code null}, never a silent {@code false}/{@code 0}/{@code 0.0}. This matters for
 * financial integrity: an omitted price must be rejected, not stored as 0.
 *
 * <p>Validation is run <b>programmatically</b> per record in {@link com.investguide.bonds.BondPriceService}
 * (Spring's {@code @Valid} does not cascade to elements of a {@code List} body, and a declarative
 * failure would 400 the whole batch instead of dropping just the bad record). The constraints here
 * drive that manual loop. Prices are integer minor units of {@link #currency}; dates are ISO
 * {@code yyyy-MM-dd}, parsed in the service so a bad date rejects only that record.
 */
public record IngestBondRequest(
        @NotBlank String isin,
        @NotNull Boolean military,
        @NotBlank @Pattern(regexp = "UAH|USD|EUR", message = "currency must be UAH, USD, or EUR") String currency,
        @NotBlank String maturity,
        @NotBlank String quotationDate,
        @NotNull @Min(0) Long sellPriceMinor,
        @NotNull @Min(0) Long buyPriceMinor,
        @NotNull Double sellYield,
        @NotNull Double buyYield
) {
}
