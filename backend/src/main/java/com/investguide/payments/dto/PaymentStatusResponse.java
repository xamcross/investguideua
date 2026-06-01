package com.investguide.payments.dto;

import com.investguide.payments.Payment;

/**
 * {@code GET /payments/{id}} response for client polling (SPECIFICATION §4.3.4, §5.1; ticket BE-P5).
 *
 * <p>Read-only projection: the client reflects {@code status} transitions
 * ({@code pending -> success|failed}) but never credits locally — crediting is server-driven by the
 * verified callback (BE-P4). {@code amountMinorUnits} stays integer minor units.
 */
public record PaymentStatusResponse(
        String paymentId,
        String orderId,
        String packId,
        String status,
        long amountMinorUnits,
        String currency,
        int tokensToCredit
) {
    public static PaymentStatusResponse from(Payment p) {
        return new PaymentStatusResponse(
                p.getId(),
                p.getOrderId(),
                p.getPackId(),
                p.getStatus(),
                p.getAmountMinorUnits(),
                p.getCurrency(),
                p.getTokensToCredit());
    }
}
