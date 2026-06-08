package com.investguide.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Precious metal price ingest + grounding [CONFIG] (feature 011).
 *
 * <p>{@code ingest.secret} is the shared machine-to-machine secret for
 * {@code POST /api/v1/admin/metal-prices}, DISTINCT from the bond ingest secret (FR-008). It is a
 * SECRET - environment only ({@code METAL_INGEST_SECRET}), never committed or logged.
 *
 * <p><b>Blank-tolerant on purpose.</b> Like {@link BondsProperties}, the secret is intentionally NOT
 * {@code @NotBlank}: an unset secret must NOT fail app startup, it must fail <i>ingest</i> closed.
 * {@link com.investguide.metals.MetalIngestAuth} rejects every ingest request when the configured
 * secret is blank (fail-closed) - a hard security invariant for a permit-all write route.
 *
 * <p>{@code primaryRateGroup} is the source rate-group key whose smallest weight tier supplies the
 * canonical per-gram sale rate backfilled onto a precious-metals investment option (FR-018). It is a
 * non-secret tunable so a source relabel does not require a code change.
 */
@Validated
@ConfigurationProperties(prefix = "metals")
public record MetalsProperties(@NotNull @Valid Ingest ingest,
                               @NotBlank String primaryRateGroup) {

    /** {@code secret} is blank-tolerant by design (see class doc); validated at request time. */
    public record Ingest(String secret) {
    }
}
