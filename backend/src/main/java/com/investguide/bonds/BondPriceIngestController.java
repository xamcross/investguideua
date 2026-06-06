package com.investguide.bonds;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.bonds.dto.IngestBondRequest;
import com.investguide.bonds.dto.IngestResult;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Machine-to-machine bond price ingest (feature 009 {@code POST /api/v1/admin/bond-prices}).
 *
 * <p>Authenticated by the {@code X-Bond-Ingest-Secret} shared secret (NOT a user JWT); the route is
 * permit-all in the security chain and self-guards via {@link BondIngestAuth}. The secret is checked
 * FIRST so an unauthorized caller never reaches body processing.
 *
 * <p>The body is read as raw bytes and parsed with the application {@link ObjectMapper} (which has
 * {@code fail-on-unknown-properties} enabled) so the secret check precedes parsing: a malformed body
 * or unknown field is a {@code 400}; the per-record validation (drop-and-count) lives in
 * {@link BondPriceService}. The body and secret header are never logged.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class BondPriceIngestController {

    private static final TypeReference<List<IngestBondRequest>> BATCH_TYPE = new TypeReference<>() {
    };

    private final BondIngestAuth auth;
    private final BondPriceService service;
    private final ObjectMapper objectMapper;

    public BondPriceIngestController(BondIngestAuth auth,
                                     BondPriceService service,
                                     ObjectMapper objectMapper) {
        this.auth = auth;
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/bond-prices", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IngestResult ingest(
            @RequestHeader(value = BondIngestAuth.HEADER, required = false) String secret,
            @RequestBody(required = false) byte[] body) {
        auth.verify(secret); // secret-first: unauthorized callers never reach parsing
        List<IngestBondRequest> batch = parse(body);
        return service.ingest(batch);
    }

    private List<IngestBondRequest> parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Bond price batch is empty.");
        }
        try {
            return objectMapper.readValue(body, BATCH_TYPE);
        } catch (Exception ex) {
            // Malformed JSON, not an array, or an unknown field (fail-on-unknown-properties).
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Malformed or unexpected request body.");
        }
    }
}
