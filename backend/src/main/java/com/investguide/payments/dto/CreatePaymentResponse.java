package com.investguide.payments.dto;

import com.investguide.payments.CheckoutData;
import com.investguide.payments.Payment;

/**
 * {@code POST /payments} response — the pinned FE-PAY2/FE-PAY3 contract (SPECIFICATION §9.2; ticket
 * BE-P2).
 *
 * <p>{@code paymentId} is the {@link Payment} document {@code _id} the client polls via
 * {@code GET /payments/{id}} (FE-PAY3); it is <strong>distinct from</strong> {@code orderId} (the
 * merchant reference / idempotency key). The client redirects the buyer to {@code pageUrl} (the
 * monobank hosted checkout) and polls status by {@code paymentId}. No signature or merchant secret is
 * ever sent to the client (AC #10).
 *
 * @param paymentId         the payment document id (poll key).
 * @param orderId           the merchant reference / gateway idempotency key.
 * @param providerInvoiceId the gateway-owned invoice id (mono {@code invoiceId}).
 * @param pageUrl           the hosted checkout page to redirect the buyer to.
 */
public record CreatePaymentResponse(
        String paymentId,
        String orderId,
        String providerInvoiceId,
        String pageUrl
) {
    public static CreatePaymentResponse from(Payment payment, CheckoutData checkout) {
        return new CreatePaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                checkout.providerInvoiceId(),
                checkout.pageUrl());
    }
}
