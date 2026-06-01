package com.investguide.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import com.investguide.config.PaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * monobank "Plata by mono" internet-acquiring implementation of {@link PaymentGateway}
 * (SPECIFICATION §9.2, §9.3, §9.4; tickets BE-P1, BE-P3, BE-P4).
 *
 * <p><b>Checkout (§9.2):</b> {@code POST /api/merchant/invoice/create} (auth header {@code X-Token})
 * with the amount in minor units, {@code ccy=980}, our orderId in {@code merchantPaymInfo.reference},
 * and the result/webhook URLs. mono returns {@code {invoiceId, pageUrl}}; the client is redirected to
 * {@code pageUrl}. The merchant token never leaves the backend and never appears in the response.
 *
 * <p><b>Callback auth (§9.3):</b> the webhook is signed with <b>ECDSA</b> — the {@code x-sign} header
 * is a base64 <b>ASN.1 DER</b> signature over SHA-256 of the <em>exact raw body bytes</em>. We verify
 * it with {@code SHA256withECDSA} against mono's public key (X.509, from {@code GET /api/merchant/pubkey}).
 * Pure JCA (SunEC) — no BouncyCastle. The DER signature is fed straight to {@link Signature#verify} (do
 * NOT convert to raw r||s). The key is cached; a verification failure triggers at most one refresh per
 * cooldown window so a flood of forged callbacks cannot amplify into outbound pubkey fetches.
 *
 * <p><b>Money stays integer.</b> mono uses integer minor units (kopiykas) on the wire, matching the
 * {@link Payment} snapshot exactly — no float, no decimal conversion (CLAUDE.md money rule).
 */
@Component
public class MonoAcquiringGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MonoAcquiringGateway.class);

    static final String SLUG = "monobank";
    private static final String CCY_UAH = "UAH";
    private static final String CCY_USD = "USD";
    private static final int CCY_NUM_UAH = 980;
    private static final int CCY_NUM_USD = 840;

    private final PaymentProperties.Mono cfg;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String invoiceCreateUrl;
    private final String invoiceStatusUrl;
    private final String pubkeyUrl;
    private final String resultUrl;
    private final String callbackUrl;

    /** Cached verification key with the wall-clock it was fetched and the last refresh attempt. */
    private final AtomicReference<CachedKey> cachedKey = new AtomicReference<>();

    public MonoAcquiringGateway(PaymentProperties properties, ObjectMapper objectMapper) {
        this.cfg = properties.mono();
        this.objectMapper = objectMapper;
        this.resultUrl = properties.resultUrl();
        this.callbackUrl = properties.callbackUrl();
        String base = trimTrailingSlash(cfg.apiBaseUrl());
        if (!isHttpsOrLoopback(base)) {
            // The pubkey trust chain depends on TLS to api.monobank.ua; never allow a downgraded scheme
            // for a real host. Plain http is permitted only for a loopback address (local dev / tests).
            throw new IllegalStateException("payment.mono.api-base-url must be https (loopback http only)");
        }
        this.invoiceCreateUrl = base + "/api/merchant/invoice/create";
        this.invoiceStatusUrl = base + "/api/merchant/invoice/status";
        this.pubkeyUrl = base + "/api/merchant/pubkey";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, cfg.requestTimeoutMs() / 2)))
                .build();
    }

    @Override
    public String slug() {
        return SLUG;
    }

    // ---- §9.2 create checkout -------------------------------------------------------------

    @Override
    public CheckoutData createCheckout(Payment payment, String deliveryEmail) {
        // deliveryEmail is unused for mono: ПРРО fiscal-receipt delivery is configured in the mono web
        // cabinet (бінд ПРРО), not per-invoice, so it is intentionally not sent here (§9.5).
        Map<String, Object> merchantPaymInfo = new LinkedHashMap<>();
        merchantPaymInfo.put("reference", payment.getOrderId());
        merchantPaymInfo.put("destination", "InvestGuideUA tokens: " + payment.getPackId());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", payment.getAmountMinorUnits());
        request.put("ccy", ccyToNumeric(payment.getCurrency()));
        request.put("merchantPaymInfo", merchantPaymInfo);
        request.put("redirectUrl", resultUrl);
        request.put("webHookUrl", callbackUrl);
        request.put("validity", cfg.invoiceValiditySeconds());

        String body = serialize(request);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(invoiceCreateUrl))
                .timeout(Duration.ofMillis(cfg.requestTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("X-Token", cfg.token())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = sendOrGatewayError(httpRequest, "create invoice");
        if (response.statusCode() / 100 != 2) {
            log.warn("payment_create_http_error status={}", response.statusCode());
            throw gatewayUnavailable("monobank rejected the invoice (HTTP " + response.statusCode() + ").");
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            String invoiceId = text(root, "invoiceId");
            String pageUrl = text(root, "pageUrl");
            if (invoiceId == null || pageUrl == null) {
                throw gatewayUnavailable("monobank returned an incomplete invoice.");
            }
            return new CheckoutData(pageUrl, invoiceId);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw gatewayUnavailable("Could not parse the monobank invoice response.");
        }
    }

    // ---- §9.3 callback verification (ECDSA over raw bytes) --------------------------------

    @Override
    public boolean verifyCallback(byte[] rawBody, String signatureHeader) {
        if (rawBody == null || rawBody.length == 0 || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(signatureHeader.trim());
        } catch (IllegalArgumentException badBase64) {
            return false; // malformed signature header — reject, do not retry
        }

        PublicKey key = currentKey();           // may throw a transient gateway error if none available
        if (verifyWith(key, rawBody, signature)) {
            return true;
        }
        // Possible key rotation: refresh at most once per cooldown, then re-verify exactly once.
        PublicKey refreshed = refreshKeyIfAllowed();
        return refreshed != null && verifyWith(refreshed, rawBody, signature);
    }

    @Override
    public CallbackResult parseResult(byte[] rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String reference = text(root, "reference");
            String invoiceId = text(root, "invoiceId");
            String status = text(root, "status");
            long amount = root.path("amount").asLong(-1L);
            String currency = numericToCcy(root.path("ccy").asInt(-1));
            Instant modified = parseInstant(text(root, "modifiedDate"));
            return new CallbackResult(reference, invoiceId, status, amount, currency, modified);
        } catch (Exception ex) {
            // A signature-verified body that will not parse is a malformed callback, not a server fault.
            throw new ApiException(ErrorCode.PAYMENT_ERROR, "Malformed payment callback payload.");
        }
    }

    // ---- §2.5 expired/desync reconciliation ----------------------------------------------

    @Override
    public CallbackResult fetchStatus(String providerInvoiceId) {
        if (providerInvoiceId == null || providerInvoiceId.isBlank()) {
            return null;
        }
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(invoiceStatusUrl + "?invoiceId=" + providerInvoiceId))
                .timeout(Duration.ofMillis(cfg.requestTimeoutMs()))
                .header("X-Token", cfg.token())
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            return parseResult(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            return null; // best-effort: never throw into the read path
        }
    }

    // ---- internals ------------------------------------------------------------------------

    private boolean verifyWith(PublicKey key, byte[] body, byte[] derSignature) {
        try {
            Signature ecdsa = Signature.getInstance("SHA256withECDSA");
            ecdsa.initVerify(key);
            ecdsa.update(body);
            return ecdsa.verify(derSignature); // DER-encoded (r,s) — exactly what mono sends
        } catch (Exception ex) {
            return false;
        }
    }

    /** Return a cached key (refreshing if stale); fetch synchronously if none is cached yet. */
    private PublicKey currentKey() {
        CachedKey current = cachedKey.get();
        Instant now = Instant.now();
        if (current != null && now.isBefore(current.fetchedAt().plusMillis(cfg.pubkeyCacheTtlMs()))) {
            return current.key();
        }
        PublicKey fetched = tryFetchKey(now);
        if (fetched != null) {
            return fetched;
        }
        if (current != null) {
            return current.key(); // serve a stale-but-usable key rather than fail
        }
        throw gatewayUnavailable("Could not obtain the monobank verification key.");
    }

    /** Refresh-on-failure, bounded by the cooldown so forged callbacks cannot trigger a fetch flood. */
    private PublicKey refreshKeyIfAllowed() {
        CachedKey current = cachedKey.get();
        Instant now = Instant.now();
        if (current != null
                && now.isBefore(current.lastRefreshAttempt().plusMillis(cfg.pubkeyMinRefreshIntervalMs()))) {
            return null; // within cooldown — do not hit mono again
        }
        return tryFetchKey(now);
    }

    /** Fetch + parse the public key; updates the cache (and the refresh-attempt clock) on success. */
    private PublicKey tryFetchKey(Instant attemptAt) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(pubkeyUrl))
                .timeout(Duration.ofMillis(cfg.requestTimeoutMs()))
                .header("X-Token", cfg.token())
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("payment_pubkey_http_error status={}", response.statusCode());
                touchRefreshAttempt(attemptAt);
                return null;
            }
            String keyBase64 = text(objectMapper.readTree(response.body()), "key");
            PublicKey key = parsePublicKey(keyBase64);
            cachedKey.set(new CachedKey(key, attemptAt, attemptAt));
            return key;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            touchRefreshAttempt(attemptAt);
            return null;
        } catch (Exception ex) {
            log.warn("payment_pubkey_fetch_failed");
            touchRefreshAttempt(attemptAt);
            return null;
        }
    }

    private void touchRefreshAttempt(Instant attemptAt) {
        CachedKey current = cachedKey.get();
        if (current != null) {
            cachedKey.set(new CachedKey(current.key(), current.fetchedAt(), attemptAt));
        }
    }

    /** Decode {@code {"key": base64(PEM)}} -> X.509 EC public key. */
    private PublicKey parsePublicKey(String keyBase64) throws Exception {
        byte[] pemBytes = Base64.getDecoder().decode(keyBase64);
        String pem = new String(pemBytes, StandardCharsets.UTF_8);
        String der = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] derBytes = Base64.getDecoder().decode(der);
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(derBytes));
    }

    private HttpResponse<String> sendOrGatewayError(HttpRequest request, String what) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw gatewayUnavailable("Interrupted while calling monobank to " + what + ".");
        } catch (Exception ex) {
            throw gatewayUnavailable("monobank is unreachable (" + what + ").");
        }
    }

    private String serialize(Map<String, Object> request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception ex) {
            throw gatewayUnavailable("Could not build the checkout request.");
        }
    }

    /** Gateway/provider fault -> PAYMENT_ERROR mapped to 502 so the user can retry (§2.8). */
    private static ApiException gatewayUnavailable(String message) {
        return new ApiException(ErrorCode.PAYMENT_ERROR, message, HttpStatus.BAD_GATEWAY);
    }

    private static int ccyToNumeric(String currency) {
        if (CCY_USD.equalsIgnoreCase(currency)) {
            return CCY_NUM_USD;
        }
        return CCY_NUM_UAH; // default/UAH
    }

    private static String numericToCcy(int numeric) {
        return switch (numeric) {
            case CCY_NUM_USD -> CCY_USD;
            case CCY_NUM_UAH -> CCY_UAH;
            default -> null;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Allow https for any host, and plain http only for a loopback address (local dev / tests). */
    private static boolean isHttpsOrLoopback(String base) {
        if (base.startsWith("https://")) {
            return true;
        }
        return base.startsWith("http://localhost") || base.startsWith("http://127.0.0.1");
    }

    private record CachedKey(PublicKey key, Instant fetchedAt, Instant lastRefreshAttempt) {
    }
}
