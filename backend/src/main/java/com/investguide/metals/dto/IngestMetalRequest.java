package com.investguide.metals.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * One quote in a metals ingest batch (feature 011 {@code POST /api/v1/admin/metal-prices}).
 *
 * <p><b>All fields are boxed object types</b> (not primitives) so an omitted JSON field deserializes
 * to a rejectable {@code null}, never a silent {@code 0}. This matters for financial integrity: an
 * omitted rate must be rejected, not stored as 0.
 *
 * <p>Validation is run <b>programmatically</b> per record in
 * {@link com.investguide.metals.MetalPriceService} (Spring's {@code @Valid} does not cascade to
 * elements of a {@code List} body, and a declarative failure would 400 the whole batch instead of
 * dropping just the bad record). Rates are integer minor units (kopiykas) per gram; {@code quotationDate}
 * is ISO {@code yyyy-MM-dd}, parsed in the service so a bad date rejects only that record.
 */
public record IngestMetalRequest(
        @NotBlank @Pattern(regexp = "GOLD|SILVER", message = "metal must be GOLD or SILVER") String metal,
        @NotBlank String rateGroup,
        @NotBlank String weightKey,
        @NotNull @Positive Double weightGrams,
        @NotBlank @Pattern(regexp = "UAH", message = "currency must be UAH") String currency,
        @NotBlank String quotationDate,
        @NotNull @Min(0) Long purchaseRateMinor,
        @NotNull @Min(0) Long saleRateMinor
) {
}
