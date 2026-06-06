package com.investguide.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bond price ingest [CONFIG] (feature 009).
 *
 * <p>{@code ingest.secret} is the shared machine-to-machine secret for
 * {@code POST /api/v1/admin/bond-prices}. It is a SECRET - environment only ({@code BOND_INGEST_SECRET}),
 * never committed or logged.
 *
 * <p><b>Blank-tolerant on purpose.</b> The secret is intentionally NOT {@code @NotBlank}: an unset
 * secret must NOT fail app startup (the rest of the app keeps running locally), it must fail
 * <i>ingest</i> closed. {@link com.investguide.bonds.BondIngestAuth} rejects every ingest request
 * when the configured secret is blank (fail-closed). This is stronger than the optional
 * {@code MONO_TOKEN} pattern: a blank ingest secret on a permit-all route would otherwise be an open
 * unauthenticated write endpoint, so blank-rejects-all is a hard security invariant.
 */
@Validated
@ConfigurationProperties(prefix = "bonds")
public record BondsProperties(@NotNull @Valid Ingest ingest) {

    /** {@code secret} is blank-tolerant by design (see class doc); validated at request time. */
    public record Ingest(String secret) {
    }
}
