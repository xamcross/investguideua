package com.investguide.payments;

/**
 * Hosted-checkout parameters produced server-side (SPECIFICATION §9.2; ticket BE-P3).
 *
 * <p>Provider-neutral: the client simply performs a top-level redirect to {@code pageUrl}; it never
 * computes a signature or sees the merchant secret (FE-PAY2). For monobank "Plata by mono" this is the
 * hosted payment page ({@code https://pay.mbnk.biz/...}) returned by {@code /invoice/create}.
 *
 * @param pageUrl           the hosted checkout page to redirect the buyer to.
 * @param providerInvoiceId the gateway-owned invoice id (mono {@code invoiceId}); persisted on the
 *                          {@link Payment} so a webhook can be matched back to it (§9.3).
 */
public record CheckoutData(String pageUrl, String providerInvoiceId) {
}
