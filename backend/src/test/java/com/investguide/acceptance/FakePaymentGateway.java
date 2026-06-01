package com.investguide.acceptance;

import com.investguide.payments.CallbackResult;
import com.investguide.payments.CheckoutData;
import com.investguide.payments.Payment;
import com.investguide.payments.PaymentGateway;

/**
 * Sandbox fake gateway for the QA1 acceptance suite (TASKS.md QA1: "... and a fake/sandbox
 * {@code PaymentGateway}"). It stands in for {@link com.investguide.payments.MonoAcquiringGateway} so
 * the payment acceptance criteria (AC #6 credit-once, AC #7 forged-signature) run through the
 * <em>real</em> {@link com.investguide.payments.PaymentService} orchestration and the <em>real</em>
 * {@link com.investguide.tokens.TokenLedgerService} status guards without contacting monobank.
 *
 * <p>The ECDSA signature scheme itself is verified separately against a fixed keypair in
 * {@code MonoAcquiringGatewayTest}; here the gateway is the trust boundary the service relies on, so
 * the fake exposes a settable {@link #signatureValid} flag plus a scriptable {@link #nextResult}. The
 * orchestrator must credit nothing whenever {@code verifyCallback} returns {@code false} (AC #7).
 */
public final class FakePaymentGateway implements PaymentGateway {

    /** Controls {@link #verifyCallback}: {@code false} simulates a forged/invalid signature (AC #7). */
    public boolean signatureValid = true;

    /** The result {@link #parseResult} (and {@link #fetchStatus}) returns for a verified callback. */
    public CallbackResult nextResult;

    @Override
    public String slug() {
        return "fake";
    }

    @Override
    public CheckoutData createCheckout(Payment payment, String deliveryEmail) {
        // Secret-free checkout: only a redirect pageUrl + invoice id (no signature/token, AC #10).
        return new CheckoutData("https://sandbox.example/checkout/" + payment.getOrderId(),
                "inv-" + payment.getOrderId());
    }

    @Override
    public boolean verifyCallback(byte[] rawBody, String signatureHeader) {
        return signatureValid;
    }

    @Override
    public CallbackResult parseResult(byte[] rawBody) {
        return nextResult;
    }

    @Override
    public CallbackResult fetchStatus(String providerInvoiceId) {
        return nextResult;
    }
}
