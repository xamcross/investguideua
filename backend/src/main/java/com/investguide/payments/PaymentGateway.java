package com.investguide.payments;

/**
 * Payment-gateway abstraction (SPECIFICATION §9.4; ticket BE-P1). The MVP implementation is
 * {@link MonoAcquiringGateway} (monobank "Plata by mono"); another provider (LiqPay, Fondy) can be
 * added later without touching {@link PaymentService} business logic — the orchestrator and the
 * {@link com.investguide.tokens.TokenLedgerService} depend only on this interface and on the
 * provider-neutral {@link Payment} snapshot.
 */
public interface PaymentGateway {

    /** Gateway slug persisted on the {@link Payment} (e.g. {@code monobank}). */
    String slug();

    /**
     * Create a hosted checkout for a {@code pending} payment and return the {@code pageUrl} to redirect
     * the buyer to plus the gateway-owned invoice id (§9.2). The merchant secret never leaves the
     * backend and never appears in the returned data.
     *
     * @param payment       the pending payment (provides amount/currency/orderId snapshot).
     * @param deliveryEmail buyer email for ПРРО fiscal-receipt auto-delivery (§9.5).
     */
    CheckoutData createCheckout(Payment payment, String deliveryEmail);

    /**
     * Authenticate a server-to-server callback over its <em>exact transmitted bytes</em> (§9.3).
     * monobank signs the raw JSON body with ECDSA ({@code SHA256withECDSA}) and sends the base64
     * signature in the {@code x-sign} header; this verifies it against the merchant public key. Returns
     * {@code false} for any signature mismatch — the caller credits nothing on {@code false} (AC #7)
     * and mutates no payment.
     *
     * @param rawBody         the exact request body bytes (never a re-serialized object).
     * @param signatureHeader the {@code x-sign} header value (base64 ECDSA, ASN.1 DER).
     */
    boolean verifyCallback(byte[] rawBody, String signatureHeader);

    /**
     * Parse the (already signature-verified) raw callback body into the fields the orchestrator needs
     * (§9.3): the merchant {@code reference} (our orderId), the gateway invoice id, the status, the
     * {@code amount}/{@code currency} to re-check against the snapshot, and {@code modifiedDate}.
     */
    CallbackResult parseResult(byte[] rawBody);

    /**
     * Fetch the current gateway-side status for an invoice (mono {@code GET /invoice/status}). Used to
     * reconcile a still-{@code pending} payment whose terminal status arrived via no webhook (notably
     * {@code expired}, which monobank never delivers by webhook — §2.5 of the migration plan).
     * Best-effort: implementations return {@code null} when the status cannot be retrieved.
     *
     * @param providerInvoiceId the gateway invoice id to query.
     */
    CallbackResult fetchStatus(String providerInvoiceId);
}
