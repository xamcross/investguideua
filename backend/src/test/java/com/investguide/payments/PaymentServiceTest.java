package com.investguide.payments;

import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import com.investguide.payments.dto.CreatePaymentResponse;
import com.investguide.tokens.TokenLedgerService;
import com.investguide.tokens.TokenPack;
import com.investguide.tokens.TokenPackRepository;
import com.investguide.user.User;
import com.investguide.user.UserRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * BE-P2/BE-P4/BE-P6 orchestration (SPECIFICATION §9.2, §9.3, §7.4, §7.5; AC #6, #7).
 *
 * <p>Uses a mock gateway and stubbed repositories/ledger so the test exercises the orchestration
 * ordering and the trust boundary without a live Mongo or monobank. The ECDSA scheme itself is
 * covered by {@link MonoAcquiringGatewayTest}.
 */
class PaymentServiceTest {

    private static final byte[] BODY = "{\"reference\":\"order-1\"}".getBytes(StandardCharsets.UTF_8);

    private PaymentRepository paymentRepository;
    private TokenPackRepository tokenPackRepository;
    private UserRepository userRepository;
    private TokenLedgerService ledger;
    private PaymentGateway gateway;
    private MongoTemplate mongoTemplate;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        tokenPackRepository = mock(TokenPackRepository.class);
        userRepository = mock(UserRepository.class);
        ledger = mock(TokenLedgerService.class);
        gateway = mock(PaymentGateway.class);
        mongoTemplate = mock(MongoTemplate.class);
        service = new PaymentService(paymentRepository, tokenPackRepository, userRepository,
                ledger, gateway, mongoTemplate);

        when(gateway.slug()).thenReturn("monobank");
        when(gateway.createCheckout(any(), any()))
                .thenReturn(new CheckoutData("https://pay.mbnk.biz/p2_1", "p2_1"));
    }

    private User verifiedUser() {
        User u = new User();
        u.setId("user-1");
        u.setEmail("buyer@example.com");
        u.setEmailVerified(true);
        return u;
    }

    // ---- BE-P2: create ------------------------------------------------------------------

    @Test
    void createNewPaymentSnapshotsPackAndReturnsCheckoutSchema() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(verifiedUser()));
        when(paymentRepository.findFirstByUserIdAndPackIdAndStatus("user-1", "pack-10", "pending"))
                .thenReturn(Optional.empty());
        when(tokenPackRepository.findById("pack-10"))
                .thenReturn(Optional.of(new TokenPack("pack-10", 10, 16_900L, "UAH", true)));
        when(paymentRepository.insert(any(Payment.class)))
                .thenAnswer(inv -> {
                    Payment p = inv.getArgument(0);
                    p.setId("pay-1");
                    return p;
                });

        CreatePaymentResponse res = service.create("user-1", "pack-10");

        assertThat(res.paymentId()).isEqualTo("pay-1");
        assertThat(res.orderId()).isNotBlank();
        assertThat(res.providerInvoiceId()).isEqualTo("p2_1");
        assertThat(res.pageUrl()).isEqualTo("https://pay.mbnk.biz/p2_1");
    }

    @Test
    void createIsIdempotentWhileAPendingPaymentExists() {
        Payment existing = Payment.pending("user-1", "pack-10", "order-existing",
                16_900L, "UAH", 10, "monobank");
        existing.setId("pay-existing");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(verifiedUser()));
        when(paymentRepository.findFirstByUserIdAndPackIdAndStatus("user-1", "pack-10", "pending"))
                .thenReturn(Optional.of(existing));

        CreatePaymentResponse res = service.create("user-1", "pack-10");

        assertThat(res.orderId()).isEqualTo("order-existing");
        // No new payment is minted, and the pack is not even looked up.
        verify(paymentRepository, never()).insert(any(Payment.class));
        verifyNoInteractions(tokenPackRepository);
    }

    @Test
    void createRejectsInactiveOrUnknownPack() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(verifiedUser()));
        when(paymentRepository.findFirstByUserIdAndPackIdAndStatus("user-1", "pack-x", "pending"))
                .thenReturn(Optional.empty());
        when(tokenPackRepository.findById("pack-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("user-1", "pack-x"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.PAYMENT_ERROR);
        verify(paymentRepository, never()).insert(any(Payment.class));
    }

    @Test
    void createRequiresVerifiedEmail() {
        User unverified = verifiedUser();
        unverified.setEmailVerified(false);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> service.create("user-1", "pack-10"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    void createInvoiceGatewayFailureLeavesPaymentPendingAndSpendsNoToken() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(verifiedUser()));
        when(paymentRepository.findFirstByUserIdAndPackIdAndStatus("user-1", "pack-10", "pending"))
                .thenReturn(Optional.empty());
        when(tokenPackRepository.findById("pack-10"))
                .thenReturn(Optional.of(new TokenPack("pack-10", 10, 16_900L, "UAH", true)));
        when(paymentRepository.insert(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        // monobank unreachable -> gateway throws PAYMENT_ERROR mapped to 502.
        when(gateway.createCheckout(any(), any()))
                .thenThrow(new ApiException(ErrorCode.PAYMENT_ERROR, "monobank unreachable",
                        HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> service.create("user-1", "pack-10"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.PAYMENT_ERROR);
                    assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });
        // No token is ever spent on payment creation (only a verified success callback credits).
        verifyNoInteractions(ledger);
    }

    // ---- BE-P4: callback ----------------------------------------------------------------

    @Test
    void forgedSignatureIsRejectedAndCreditsNothing() {
        when(gateway.verifyCallback(any(), eq("forged"))).thenReturn(false);

        assertThatThrownBy(() -> service.handleCallback(BODY, "forged"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.PAYMENT_ERROR);

        // AC #7: no parse, no payment lookup, no credit — and no payment document is mutated.
        verifyNoInteractions(ledger);
        verify(paymentRepository, never()).findByOrderId(any());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void verifiedSuccessCreditsExactlyTheSnapshotTokens() {
        Payment payment = Payment.pending("user-1", "pack-10", "order-1",
                16_900L, "UAH", 10, "monobank");
        when(gateway.verifyCallback(any(), eq("sig"))).thenReturn(true);
        when(gateway.parseResult(any()))
                .thenReturn(new CallbackResult("order-1", "p2_1", "success", 16_900L, "UAH", null));
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
        stubWatermarkAdvances();

        service.handleCallback(BODY, "sig");

        verify(ledger).creditFromPayment("order-1", "user-1", 10);
    }

    @Test
    void staleOutOfOrderWebhookIsIgnored() {
        Payment payment = Payment.pending("user-1", "pack-10", "order-1",
                16_900L, "UAH", 10, "monobank");
        when(gateway.verifyCallback(any(), eq("sig"))).thenReturn(true);
        when(gateway.parseResult(any())).thenReturn(new CallbackResult(
                "order-1", "p2_1", "success", 16_900L, "UAH", Instant.parse("2026-01-01T00:00:00Z")));
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
        // The guarded watermark advance matches 0 docs -> this webhook is older than one already seen.
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Payment.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        service.handleCallback(BODY, "sig");

        verify(ledger, never()).creditFromPayment(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void amountMismatchIsNotHonoredAndCreditsNothing() {
        Payment payment = Payment.pending("user-1", "pack-10", "order-1",
                16_900L, "UAH", 10, "monobank");
        when(gateway.verifyCallback(any(), eq("sig"))).thenReturn(true);
        when(gateway.parseResult(any()))
                .thenReturn(new CallbackResult("order-1", "p2_1", "success", 9_900L, "UAH", null));
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
        stubWatermarkAdvances();

        service.handleCallback(BODY, "sig");

        verify(ledger, never()).creditFromPayment(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void reversalStatusTriggersClawBackNotABlanketFailed() {
        Payment payment = Payment.pending("user-1", "pack-10", "order-1",
                16_900L, "UAH", 10, "monobank");
        payment.setStatus("success");
        when(gateway.verifyCallback(any(), eq("sig"))).thenReturn(true);
        when(gateway.parseResult(any()))
                .thenReturn(new CallbackResult("order-1", "p2_1", "reversed", 16_900L, "UAH", null));
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
        stubWatermarkAdvances();

        service.handleCallback(BODY, "sig");

        verify(ledger).reversePayment("order-1", "user-1");
        verify(ledger, never()).creditFromPayment(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void unmatchedOrderCreditsNothingAndDoesNotThrow() {
        when(gateway.verifyCallback(any(), eq("sig"))).thenReturn(true);
        when(gateway.parseResult(any()))
                .thenReturn(new CallbackResult("ghost-order", null, "success", 16_900L, "UAH", null));
        when(paymentRepository.findByOrderId("ghost-order")).thenReturn(Optional.empty());

        service.handleCallback(BODY, "sig"); // no throw

        verifyNoInteractions(ledger);
    }

    // ---- BE-P5: owner-scoped read -------------------------------------------------------

    @Test
    void getOwnedReturns404ForNonOwner() {
        when(paymentRepository.findByIdAndUserId("pay-1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwned("user-2", "pay-1"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void getOwnedReconcilesAStalePendingViaStatusPoll() {
        // monobank sends no webhook for `expired`; a still-pending payment is reconciled on read (§2.5).
        Payment pending = Payment.pending("user-1", "pack-10", "order-1", 16_900L, "UAH", 10, "monobank");
        pending.setId("pay-1");
        pending.setProviderInvoiceId("p2_1");
        when(paymentRepository.findByIdAndUserId("pay-1", "user-1")).thenReturn(Optional.of(pending));
        // The gateway status poll reports the invoice expired.
        when(gateway.fetchStatus("p2_1"))
                .thenReturn(new CallbackResult("order-1", "p2_1", "expired", 16_900L, "UAH", null));
        stubWatermarkAdvances();

        service.getOwned("user-1", "pay-1");

        verify(gateway).fetchStatus("p2_1");                 // reconciliation actually polled mono
        verify(ledger, never()).creditFromPayment(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    /** Stub the guarded watermark/payload update (advanceWatermark) to "matched one doc" (current). */
    private void stubWatermarkAdvances() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Payment.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    }
}
