package com.investguide.metals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import com.investguide.metals.dto.IngestMetalRequest;
import com.investguide.metals.dto.IngestResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Machine-to-machine metal price ingest (feature 011 {@code POST /api/v1/admin/metal-prices}).
 *
 * <p>Authenticated by the {@code X-Metal-Ingest-Secret} shared secret (NOT a user JWT, DISTINCT from
 * the bond ingest secret); the route is permit-all in the security chain and self-guards via
 * {@link MetalIngestAuth}. The secret is checked FIRST so an unauthorized caller never reaches body
 * processing.
 *
 * <p>The body is read as raw bytes and parsed with the application {@link ObjectMapper} (which has
 * {@code fail-on-unknown-properties} enabled): a malformed body or unknown field is a {@code 400}; the
 * per-record validation (drop-and-count) lives in {@link MetalPriceService}. The body and secret header
 * are never logged.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class MetalPriceIngestController {

    private static final TypeReference<List<IngestMetalRequest>> BATCH_TYPE = new TypeReference<>() {
    };

    private final MetalIngestAuth auth;
    private final MetalPriceService service;
    private final ObjectMapper objectMapper;

    public MetalPriceIngestController(MetalIngestAuth auth,
                                      MetalPriceService service,
                                      ObjectMapper objectMapper) {
        this.auth = auth;
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/metal-prices", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IngestResult ingest(
            @RequestHeader(value = MetalIngestAuth.HEADER, required = false) String secret,
            @RequestBody(required = false) byte[] body) {
        auth.verify(secret); // secret-first: unauthorized callers never reach parsing
        List<IngestMetalRequest> batch = parse(body);
        return service.ingest(batch);
    }

    private List<IngestMetalRequest> parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Metal price batch is empty.");
        }
        try {
            return objectMapper.readValue(body, BATCH_TYPE);
        } catch (Exception ex) {
            // Malformed JSON, not an array, or an unknown field (fail-on-unknown-properties).
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Malformed or unexpected request body.");
        }
    }
}
