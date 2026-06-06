package com.investguide.bonds;

import com.investguide.common.error.ApiException;
import com.investguide.config.BondsProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared-secret gate for the machine-to-machine ingest endpoint (feature 009).
 *
 * <p>Authenticates {@code POST /api/v1/admin/bond-prices} by the {@code X-Bond-Ingest-Secret} header
 * - NOT a user JWT. The route is permit-all in the security chain (it carries no Bearer token), so
 * this helper is the only gate. Called first from the controller so a failure throws
 * {@link ApiException} and the global handler formats the standard error envelope.
 *
 * <p><b>Fail-closed:</b> if the configured secret is blank/unset, every request is rejected (401) -
 * the endpoint is never open. The comparison is constant-time ({@link MessageDigest#isEqual}) over
 * raw UTF-8 bytes and does not early-return on a length mismatch, so it leaks no timing oracle.
 */
@Component
public class BondIngestAuth {

    static final String HEADER = "X-Bond-Ingest-Secret";

    private final BondsProperties properties;

    public BondIngestAuth(BondsProperties properties) {
        this.properties = properties;
    }

    /**
     * Verifies the presented secret; throws {@link ApiException} (UNAUTHORIZED) on any failure.
     * Never logs the configured or presented secret.
     */
    public void verify(String presented) {
        String configured = properties.ingest().secret();
        if (!StringUtils.hasText(configured)) {
            // Fail-closed: no server secret configured -> ingest is disabled entirely.
            throw ApiException.unauthorized("Bond ingest is not configured.");
        }
        boolean ok = presented != null && constantTimeEquals(configured, presented);
        if (!ok) {
            throw ApiException.unauthorized("Invalid bond ingest credentials.");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
