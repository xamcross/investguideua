package com.investguide.payments;

/**
 * The exact {@code payments.status} string values (SPECIFICATION §6, §7.4, §7.5).
 *
 * <p>These are stored verbatim on {@link Payment} and matched by the
 * {@link com.investguide.tokens.TokenLedgerService} status-guard queries
 * ({@code {orderId, status:"pending"} -> success}, {@code {orderId, status:"success"} -> reversed}).
 * Kept as plain {@code String} constants — not an enum — so the persisted value and the ledger's
 * literal guards can never drift apart.
 */
public final class PaymentStatus {

    public static final String PENDING = "pending";
    public static final String SUCCESS = "success";
    public static final String FAILED = "failed";
    public static final String REVERSED = "reversed";

    private PaymentStatus() {
    }
}
