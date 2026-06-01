package com.investguide.payments;

import java.time.Instant;

/**
 * The parsed, signature-verified callback payload (SPECIFICATION §9.3; ticket BE-P4).
 *
 * <p>{@code amountMinorUnits} is the gateway amount already in integer minor units (kopiykas) — never
 * float — so it can be compared byte-exactly against the {@link Payment} snapshot (CLAUDE.md money
 * rule). {@code currency} is normalised to the app's alphabetic code (e.g. {@code UAH}); monobank
 * sends ISO-4217 numeric {@code 980}, which the gateway maps before constructing this record so the
 * snapshot currency check keeps working unchanged.
 *
 * @param orderId           the merchant reference (joins to {@link Payment#getOrderId()}; mono carries
 *                          it in {@code merchantPaymInfo.reference}).
 * @param providerInvoiceId the gateway-owned invoice id (mono {@code invoiceId}) — fallback join key.
 * @param status            the raw gateway status (e.g. {@code success}, {@code reversed}, {@code failure}).
 * @param amountMinorUnits  the callback amount in minor units.
 * @param currency          the callback currency, normalised to the alphabetic code (e.g. {@code UAH}).
 * @param modifiedDate      the gateway {@code modifiedDate}; used as a defensive low-water-mark against
 *                          out-of-order webhooks ({@code null} if absent).
 */
public record CallbackResult(String orderId,
                             String providerInvoiceId,
                             String status,
                             long amountMinorUnits,
                             String currency,
                             Instant modifiedDate) {
}
