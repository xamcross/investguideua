package com.investguide.payments;

import com.investguide.payments.dto.CreatePaymentRequest;
import com.investguide.payments.dto.CreatePaymentResponse;
import com.investguide.payments.dto.PaymentStatusResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Payment endpoints (SPECIFICATION §5.1, §4.3; tickets BE-P2, BE-P4, BE-P5).
 *
 * <ul>
 *   <li>{@code POST /payments} — authenticated + verified-email; creates/reuses a pending payment and
 *       returns the checkout schema (redirect {@code pageUrl}).</li>
 *   <li>{@code POST /payments/mono/callback} — <b>public</b> (signed) monobank server-to-server webhook;
 *       the ECDSA {@code x-sign} signature is the only trust boundary (verified inside
 *       {@link PaymentService}).</li>
 *   <li>{@code GET /payments/{id}} — owner-only status read for client polling.</li>
 * </ul>
 *
 * All money-correctness (idempotent credit, reversal, status guards) lives in {@link PaymentService}
 * and {@link com.investguide.tokens.TokenLedgerService}; the controller is a thin transport layer.
 */
@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** §4.3.1 {@code POST /payments} — create/reuse a pending payment (BE-P2). */
    @PostMapping("/payments")
    public CreatePaymentResponse create(@AuthenticationPrincipal String userId,
                                        @Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.create(userId, request.packId());
    }

    /**
     * §4.3.2 {@code POST /payments/mono/callback} — public, signed (BE-P4). monobank POSTs
     * {@code application/json}; the body is signed with ECDSA and the base64 signature is in the
     * {@code x-sign} header. The body is read as the <b>exact raw bytes</b> ({@code byte[]}) — never a
     * re-serialized object — because the signature is verified over those bytes. A 200 body acknowledges
     * receipt (monobank retries up to 3× on non-200); an invalid signature surfaces as
     * {@code 400 PAYMENT_ERROR} (rejected, no credit) and a transient gateway fault as {@code 502}.
     */
    @PostMapping(value = "/payments/mono/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> monoCallback(@RequestBody byte[] body,
                                            @RequestHeader(value = "x-sign", required = false) String xSign) {
        paymentService.handleCallback(body, xSign);
        return Map.of("result", "ok");
    }

    /** §4.3.4 {@code GET /payments/{id}} — owner-only status read (BE-P5). Non-owner → 404. */
    @GetMapping("/payments/{id}")
    public PaymentStatusResponse status(@AuthenticationPrincipal String userId,
                                        @PathVariable String id) {
        return PaymentStatusResponse.from(paymentService.getOwned(userId, id));
    }
}
