package com.investguide.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.config.PaymentProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the monobank ECDSA webhook scheme (SPECIFICATION §9.3) against an <em>in-test</em>
 * EC keypair, served from a loopback {@link HttpServer} stub (no live monobank, no rotating real key).
 *
 * <p>Asserts: a valid {@code SHA256withECDSA} DER signature over the exact body verifies; a one-byte
 * body mutation is rejected; a random/forged signature is rejected; the public key is cached (fetched
 * once); checkout creation parses {@code invoiceId}/{@code pageUrl}; and {@code parseResult} maps the
 * ISO-4217 numeric {@code 980} back to {@code "UAH"} so the snapshot currency check holds.
 */
class MonoAcquiringGatewayTest {

    private HttpServer server;
    private String baseUrl;
    private KeyPair keyPair;
    private final AtomicInteger pubkeyFetches = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = kpg.generateKeyPair();

        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        String keyFieldBase64 = Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/merchant/pubkey", ex -> {
            pubkeyFetches.incrementAndGet();
            byte[] body = ("{\"key\":\"" + keyFieldBase64 + "\"}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/api/merchant/invoice/create", ex -> {
            byte[] body = "{\"invoiceId\":\"p2_test\",\"pageUrl\":\"https://pay.mbnk.biz/p2_test\"}"
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private MonoAcquiringGateway gateway() {
        PaymentProperties props = new PaymentProperties(
                new PaymentProperties.Mono("test-token", baseUrl, 3600, 5000, 3_600_000, 60_000),
                "https://app.example/result",
                "https://app.example/api/v1/payments/mono/callback");
        return new MonoAcquiringGateway(props, new ObjectMapper());
    }

    private String sign(byte[] body) throws Exception {
        Signature ecdsa = Signature.getInstance("SHA256withECDSA");
        ecdsa.initSign((PrivateKey) keyPair.getPrivate());
        ecdsa.update(body);
        return Base64.getEncoder().encodeToString(ecdsa.sign()); // DER-encoded, as monobank sends
    }

    @Test
    void validSignatureVerifies() throws Exception {
        MonoAcquiringGateway gw = gateway();
        byte[] body = "{\"invoiceId\":\"p2_test\",\"status\":\"success\",\"reference\":\"order-1\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(gw.verifyCallback(body, sign(body))).isTrue();
    }

    @Test
    void tamperedBodyIsRejected() throws Exception {
        MonoAcquiringGateway gw = gateway();
        byte[] body = "{\"status\":\"success\",\"amount\":16900}".getBytes(StandardCharsets.UTF_8);
        String signature = sign(body);

        byte[] tampered = "{\"status\":\"success\",\"amount\":99900}".getBytes(StandardCharsets.UTF_8);
        assertThat(gw.verifyCallback(tampered, signature)).isFalse();
    }

    @Test
    void forgedSignatureIsRejected() {
        MonoAcquiringGateway gw = gateway();
        byte[] body = "{\"status\":\"success\"}".getBytes(StandardCharsets.UTF_8);
        // A syntactically valid base64 blob that is not a real signature for this body/key.
        String forged = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertThat(gw.verifyCallback(body, forged)).isFalse();
    }

    @Test
    void publicKeyIsCachedAcrossVerifications() throws Exception {
        MonoAcquiringGateway gw = gateway();
        byte[] body = "{\"status\":\"success\"}".getBytes(StandardCharsets.UTF_8);
        String signature = sign(body);

        gw.verifyCallback(body, signature);
        gw.verifyCallback(body, signature);

        assertThat(pubkeyFetches.get()).isEqualTo(1); // fetched once, then served from cache
    }

    @Test
    void createCheckoutReturnsPageUrlAndInvoiceId() {
        MonoAcquiringGateway gw = gateway();
        Payment payment = Payment.pending("user-1", "pack-10", "order-1", 16_900L, "UAH", 10, "monobank");

        CheckoutData checkout = gw.createCheckout(payment, "buyer@example.com");

        assertThat(checkout.pageUrl()).isEqualTo("https://pay.mbnk.biz/p2_test");
        assertThat(checkout.providerInvoiceId()).isEqualTo("p2_test");
    }

    @Test
    void parseResultMapsNumericCurrencyAndReference() {
        MonoAcquiringGateway gw = gateway();
        byte[] body = ("{\"invoiceId\":\"p2_test\",\"status\":\"success\",\"amount\":16900,"
                + "\"ccy\":980,\"reference\":\"order-1\","
                + "\"modifiedDate\":\"2026-06-01T10:00:00Z\"}").getBytes(StandardCharsets.UTF_8);

        CallbackResult result = gw.parseResult(body);

        assertThat(result.orderId()).isEqualTo("order-1");
        assertThat(result.providerInvoiceId()).isEqualTo("p2_test");
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.amountMinorUnits()).isEqualTo(16_900L);
        assertThat(result.currency()).isEqualTo("UAH"); // 980 -> UAH
        assertThat(result.modifiedDate()).isNotNull();
    }
}
