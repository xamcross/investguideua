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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Payment orchestration (SPECIFICATION §4.3, §7.4, §7.5, §9; tickets BE-P2, BE-P4, BE-P5, BE-P6).
 *
 * <p>All balance mutations are delegated to {@link TokenLedgerService} (the single source of truth,
 * BE-T2). This service only ever mutates <em>payment</em> status/payload — never {@code tokenBalance}
 * directly — keeping the CLAUDE.md "no balance mutation outside the ledger" invariant.
 *
 * <p>Correctness for the money-critical paths comes entirely from the ledger's status guards: a
 * replayed {@code success} callback credits nothing extra (idempotent per {@code orderId}, AC #6), and
 * a reversal runs at most once (§7.5). The provider is monobank "Plata by mono" via
 * {@link MonoAcquiringGateway}, reached only through the {@link PaymentGateway} abstraction.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** monobank statuses that mean "paid" and should credit tokens (§9.3). */
    private static final Set<String> SUCCESS_STATUSES = Set.of("success");
    /** Statuses that claw back an already-credited payment (§7.5). monobank uses {@code reversed}. */
    private static final Set<String> REVERSAL_STATUSES = Set.of("reversed");
    /** Terminal failure statuses. {@code expired} arrives only via status poll, never webhook (§2.5). */
    private static final Set<String> FAILURE_STATUSES = Set.of("failure", "expired");

    private final PaymentRepository paymentRepository;
    private final TokenPackRepository tokenPackRepository;
    private final UserRepository userRepository;
    private final TokenLedgerService tokenLedger;
    private final PaymentGateway gateway;
    private final MongoTemplate mongoTemplate;

    public PaymentService(PaymentRepository paymentRepository,
                          TokenPackRepository tokenPackRepository,
                          UserRepository userRepository,
                          TokenLedgerService tokenLedger,
                          PaymentGateway gateway,
                          MongoTemplate mongoTemplate) {
        this.paymentRepository = paymentRepository;
        this.tokenPackRepository = tokenPackRepository;
        this.userRepository = userRepository;
        this.tokenLedger = tokenLedger;
        this.gateway = gateway;
        this.mongoTemplate = mongoTemplate;
    }

    // ---- BE-P2: create a payment (idempotent per (userId, packId) while pending) ----------

    /**
     * Create (or reuse) a {@code pending} payment and return the checkout schema (§9.2). The
     * verified-email gate (BE-A6) is enforced here: unverified users cannot buy tokens.
     *
     * <p><b>Idempotency:</b> our {@link Payment} (and its {@code orderId}) is reused for the same
     * {@code (userId, packId)} while a {@code pending} one exists, so a repeat click never duplicates
     * the order. A fresh monobank invoice is minted on each call (mono {@code /invoice/create} is not
     * idempotent); whichever invoice the buyer pays carries the same {@code reference == orderId}, so
     * the ledger credits exactly once. The latest {@code providerInvoiceId} is patched onto the payment.
     *
     * <p>If the gateway is unreachable, {@code createCheckout} throws {@code PAYMENT_ERROR}/502 and the
     * payment is left {@code pending} — <b>no token is spent on payment creation</b> (only a verified
     * {@code success} callback credits), so a failed create costs nothing and is safely retryable.
     */
    public CreatePaymentResponse create(String userId, String packId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Session no longer valid."));
        if (!user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED, "Verify your email to buy tokens.");
        }

        // Reuse a live pending order rather than minting a duplicate.
        Payment existing = paymentRepository
                .findFirstByUserIdAndPackIdAndStatus(userId, packId, PaymentStatus.PENDING)
                .orElse(null);
        if (existing != null) {
            return checkoutFor(existing, user.getEmail());
        }

        TokenPack pack = tokenPackRepository.findById(packId)
                .filter(TokenPack::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_ERROR,
                        "That token pack is not available."));

        Payment payment = Payment.pending(
                userId, pack.getId(), UUID.randomUUID().toString(),
                pack.getPriceMinorUnits(), pack.getCurrency(), pack.getTokens(),
                gateway.slug());
        Payment saved;
        try {
            saved = paymentRepository.insert(payment);
        } catch (DuplicateKeyException raceOnOrderId) {
            // Two concurrent creates collided on the unique orderId (astronomically rare with a UUID).
            // Fall back to the now-existing pending order so the caller still gets a usable checkout.
            saved = paymentRepository
                    .findFirstByUserIdAndPackIdAndStatus(userId, packId, PaymentStatus.PENDING)
                    .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_ERROR,
                            "Could not start the payment. Please try again."));
        }

        log.info("payment_created paymentId={} orderId={} userId={} packId={}",
                saved.getId(), saved.getOrderId(), userId, packId);
        return checkoutFor(saved, user.getEmail());
    }

    /** Create a gateway checkout and patch the returned invoice id onto the payment. */
    private CreatePaymentResponse checkoutFor(Payment payment, String email) {
        CheckoutData checkout = gateway.createCheckout(payment, email);
        // Patch the gateway invoice id so a webhook can also be matched by it (fallback to reference).
        payment.setProviderInvoiceId(checkout.providerInvoiceId());
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("orderId").is(payment.getOrderId())),
                new Update().set("providerInvoiceId", checkout.providerInvoiceId())
                        .set("updatedAt", Instant.now()),
                Payment.class);
        return CreatePaymentResponse.from(payment, checkout);
    }

    // ---- BE-P4 / BE-P6: server-to-server callback (security-critical) ---------------------

    /**
     * Verify and process a monobank webhook (§9.3, §7.4, §7.5).
     *
     * <ol>
     *   <li><b>Signature</b>: ECDSA verify over the <em>exact raw body bytes</em> with the merchant
     *       public key. A mismatch is rejected with {@code PAYMENT_ERROR} and mutates no payment, so a
     *       forged callback cannot flip a legitimate pending order (AC #7). A transient inability to
     *       obtain the key surfaces as {@code PAYMENT_ERROR}/502 so monobank retries (§2.8).</li>
     *   <li>Parse, match by {@code reference} (our orderId), falling back to the gateway invoice id.</li>
     *   <li><b>Stale-guard</b>: advance the {@code gatewayModifiedAt} low-water-mark via a guarded
     *       single-doc update; an out-of-order (older) webhook is acknowledged but not acted on.</li>
     *   <li>Route by <b>terminal</b> status only: {@code success} (+amount/ccy match) credits via the
     *       ledger; {@code reversed} claws back; {@code failure}/{@code expired} marks failed. Non-terminal
     *       {@code created}/{@code processing}/{@code hold} are acknowledged with no state change.</li>
     * </ol>
     */
    public void handleCallback(byte[] rawBody, String signatureHeader) {
        if (!gateway.verifyCallback(rawBody, signatureHeader)) {
            // Reject without touching any payment document (do not trust attacker-controlled data).
            log.warn("payment_callback_rejected reason=signature_mismatch");
            throw new ApiException(ErrorCode.PAYMENT_ERROR, "Invalid callback signature.");
        }

        CallbackResult result = gateway.parseResult(rawBody);
        Payment payment = matchPayment(result);
        if (payment == null) {
            // Unmatched order — nothing to credit. Acknowledge so the gateway stops retrying.
            log.warn("payment_callback_unmatched orderId={} invoiceId={}",
                    result.orderId(), result.providerInvoiceId());
            return;
        }

        String rawJson = new String(rawBody, StandardCharsets.UTF_8);
        if (!advanceWatermark(payment.getOrderId(), rawJson, result.modifiedDate())) {
            log.info("payment_callback_stale orderId={} modifiedDate={}",
                    payment.getOrderId(), result.modifiedDate());
            return; // older than an already-processed webhook — defensively ignore
        }

        String status = result.status() == null ? "" : result.status().toLowerCase();
        if (SUCCESS_STATUSES.contains(status)) {
            creditOrFail(payment, result);
        } else if (REVERSAL_STATUSES.contains(status)) {
            boolean reversed = tokenLedger.reversePayment(payment.getOrderId(), payment.getUserId());
            log.info("payment_callback_reversal orderId={} userId={} applied={}",
                    payment.getOrderId(), payment.getUserId(), reversed);
        } else if (FAILURE_STATUSES.contains(status)) {
            markFailed(payment.getOrderId());
            log.info("payment_callback_failed orderId={} gatewayStatus={}", payment.getOrderId(), status);
        } else {
            // Non-terminal (created/processing/hold) — acknowledge, no state change.
            log.info("payment_callback_pending orderId={} gatewayStatus={}", payment.getOrderId(), status);
        }
    }

    /** Match by merchant reference (orderId); fall back to the gateway invoice id. */
    private Payment matchPayment(CallbackResult result) {
        Payment byRef = result.orderId() == null ? null
                : paymentRepository.findByOrderId(result.orderId()).orElse(null);
        if (byRef != null) {
            return byRef;
        }
        return result.providerInvoiceId() == null ? null
                : paymentRepository.findByProviderInvoiceId(result.providerInvoiceId()).orElse(null);
    }

    /**
     * Credit a verified {@code success} callback, but only if the amount/currency match the snapshot
     * (§9.3) — a mismatch is not honored and the payment is marked {@code failed}. Crediting itself is
     * the ledger's status-guarded, idempotent {@code creditFromPayment} (AC #6).
     */
    private void creditOrFail(Payment payment, CallbackResult result) {
        boolean amountMatches = result.amountMinorUnits() == payment.getAmountMinorUnits();
        boolean currencyMatches = payment.getCurrency() != null
                && payment.getCurrency().equalsIgnoreCase(result.currency());
        if (!amountMatches || !currencyMatches) {
            markFailed(payment.getOrderId());
            log.warn("payment_callback_amount_mismatch orderId={} snapshotMinor={} callbackMinor={} "
                            + "snapshotCcy={} callbackCcy={}",
                    payment.getOrderId(), payment.getAmountMinorUnits(), result.amountMinorUnits(),
                    payment.getCurrency(), result.currency());
            return;
        }
        boolean credited = tokenLedger.creditFromPayment(
                payment.getOrderId(), payment.getUserId(), payment.getTokensToCredit());
        log.info("payment_callback_success orderId={} userId={} tokens={} credited={}",
                payment.getOrderId(), payment.getUserId(), payment.getTokensToCredit(), credited);
    }

    // ---- BE-P5: owner-scoped status read (+ §2.5 expired reconciliation) ------------------

    /**
     * The caller's own payment (SPECIFICATION §4.3.4, §5.1; ticket BE-P5). A non-owned/unknown id
     * yields {@code 404} without revealing existence.
     *
     * <p>monobank never sends a webhook for {@code expired} (§9.3), so a still-{@code pending} payment is
     * reconciled best-effort against the gateway status on read: an {@code expired}/{@code failure} flips
     * it to {@code failed}. The reconciliation is non-blocking and never throws into this read path.
     */
    public Payment getOwned(String userId, String paymentId) {
        Payment payment = paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> ApiException.notFound("Payment not found."));
        if (PaymentStatus.PENDING.equals(payment.getStatus()) && payment.getProviderInvoiceId() != null) {
            reconcilePending(payment);
            payment = paymentRepository.findByIdAndUserId(paymentId, userId).orElse(payment);
        }
        return payment;
    }

    /** Best-effort gateway status check for a stale pending payment (§2.5). Never throws. */
    private void reconcilePending(Payment payment) {
        try {
            CallbackResult status = gateway.fetchStatus(payment.getProviderInvoiceId());
            if (status == null) {
                return;
            }
            String s = status.status() == null ? "" : status.status().toLowerCase();
            if (SUCCESS_STATUSES.contains(s)) {
                creditOrFail(payment, status);
            } else if (REVERSAL_STATUSES.contains(s)) {
                tokenLedger.reversePayment(payment.getOrderId(), payment.getUserId());
            } else if (FAILURE_STATUSES.contains(s)) {
                markFailed(payment.getOrderId());
            }
        } catch (RuntimeException ex) {
            log.warn("payment_reconcile_failed orderId={}", payment.getOrderId());
        }
    }

    // ---- BE-P6: internal/admin reversal entry point ---------------------------------------

    /**
     * Trigger a reversal for an order (SPECIFICATION §7.5; ticket BE-P6) outside the callback path —
     * e.g. an admin op. Idempotent and floors the claw-back at the remaining balance; runs at most once
     * per {@code orderId}.
     *
     * @return {@code true} if this call performed the reversal flip; {@code false} on replay / not-success.
     */
    public boolean reverseByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Payment not found."));
        return tokenLedger.reversePayment(payment.getOrderId(), payment.getUserId());
    }

    // ---- internals (payment-status/payload writes; never tokenBalance) --------------------

    /**
     * Persist the raw verified payload + advance the {@code gatewayModifiedAt} low-water-mark in one
     * guarded single-doc update (no read-modify-write). Returns {@code true} if this webhook is current
     * (newer than, or the first with, a modifiedDate — or carries no modifiedDate to compare), and
     * {@code false} if it is strictly older than an already-processed webhook and should be ignored.
     */
    private boolean advanceWatermark(String orderId, String rawJson, Instant modifiedDate) {
        if (modifiedDate == null) {
            // Nothing to compare against — persist audit payload and proceed.
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("orderId").is(orderId)),
                    new Update().set("gatewayPayload", rawJson).set("updatedAt", Instant.now()),
                    Payment.class);
            return true;
        }
        Query guard = Query.query(Criteria.where("orderId").is(orderId)
                .orOperator(
                        Criteria.where("gatewayModifiedAt").exists(false),
                        Criteria.where("gatewayModifiedAt").lt(modifiedDate)));
        Update advance = new Update()
                .set("gatewayModifiedAt", modifiedDate)
                .set("gatewayPayload", rawJson)
                .set("updatedAt", Instant.now());
        UpdateResult result = mongoTemplate.updateFirst(guard, advance, Payment.class);
        return result.getModifiedCount() == 1;
    }

    /**
     * Status-guarded flip {@code {orderId, status:"pending"} -> failed}. Guarded so a callback arriving
     * after a {@code success}/{@code reversed} cannot regress the payment to {@code failed}.
     *
     * @return {@code true} if this call flipped a pending payment to failed.
     */
    private boolean markFailed(String orderId) {
        Query guard = Query.query(Criteria.where("orderId").is(orderId).and("status").is(PaymentStatus.PENDING));
        Update flip = new Update().set("status", PaymentStatus.FAILED).set("updatedAt", Instant.now());
        UpdateResult result = mongoTemplate.updateFirst(guard, flip, Payment.class);
        return result.getModifiedCount() == 1;
    }
}
