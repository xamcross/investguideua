package com.investguide.tokens;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Purchasable token pack (SPECIFICATION §6 {@code tokenPacks}, §9.1; ticket BE-T1).
 *
 * <p>Seeded pricing config (§9.1 shipping defaults: {@code pack-5/10/25} at 99/169/379 UAH),
 * validated at seed-time against the §9.1 unit-economics rule (X7). The catalog is DB-managed for
 * the MVP — no admin UI (§1.2, §3).
 *
 * <p><b>Money is integer minor units only.</b> {@code priceMinorUnits} is a {@code long} of
 * kopiykas (e.g. 99 UAH is stored as {@code 9900}); there is no floating-point money field on
 * this type or its serialization (BE-T1 DoD). {@code _id} is a stable string slug
 * (e.g. {@code "pack-10"}). The seeder is insert-only (upsert-by-{@code _id} with
 * {@code $setOnInsert}), so re-runs never clobber a manually toggled {@code active} flag (X7).
 */
@Document(collection = "tokenPacks")
public class TokenPack {

    @Id
    private String id;

    @Field("tokens")
    private int tokens;

    /** Price in minor units (kopiykas). Integer only — never a float. */
    @Field("priceMinorUnits")
    private long priceMinorUnits;

    @Field("currency")
    private String currency;

    @Indexed
    @Field("active")
    private boolean active;

    public TokenPack() {
    }

    /** Full constructor used by the seeder (X7). */
    public TokenPack(String id, int tokens, long priceMinorUnits, String currency, boolean active) {
        this.id = id;
        this.tokens = tokens;
        this.priceMinorUnits = priceMinorUnits;
        this.currency = currency;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public long getPriceMinorUnits() {
        return priceMinorUnits;
    }

    public void setPriceMinorUnits(long priceMinorUnits) {
        this.priceMinorUnits = priceMinorUnits;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
