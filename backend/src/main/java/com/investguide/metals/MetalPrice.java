package com.investguide.metals;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Latest known PrivatBank quote for one (metal, rate group, weight) combination (feature 011
 * {@code metalPrices}).
 *
 * <p><b>Keying:</b> the document {@code _id} is the deterministic composite key
 * {@code "<METAL>:<rateGroup>:<weightKey>"} (e.g. {@code GOLD:one:1}, {@code SILVER:one:10}). Uniqueness
 * is therefore implicit and the ingest upsert keeps exactly one record per combination - no
 * {@code @Indexed(unique=true)} and no delete path (FR-007, SC-002, SC-006). The {@code weightKey} token
 * is preserved verbatim from the source so {@code "2.5"} never fragments (FR-004a).
 *
 * <p><b>Money units (project rule):</b> {@code purchaseRateMinor}/{@code saleRateMinor} are integer
 * minor units (kopiykas) per gram - never floats. {@code weightGrams} is a non-money dimension label
 * (used to select the smallest tier) and so stays a double, like bond yields.
 *
 * <p>{@code fetchedAt} is stamped server-side at ingest (one instant per batch) so an admin can judge
 * freshness even if a daily run was missed.
 */
@Document(collection = "metalPrices")
public class MetalPrice {

    @Id
    private String id;

    /** {@code GOLD} or {@code SILVER}. */
    @Field("metal")
    private String metal;

    /** Source rate-group key, opaque ({@code one}/{@code two}/{@code three}). */
    @Field("rateGroup")
    private String rateGroup;

    /** Source weight token, verbatim (e.g. {@code 1}, {@code 2.5}, {@code 1000}). */
    @Field("weightKey")
    private String weightKey;

    /** Numeric weight in grams, for ordering / smallest-tier selection. */
    @Field("weightGrams")
    private double weightGrams;

    /** Quote currency: {@code UAH} for this feed. */
    @Field("currency")
    private String currency;

    /** Bank purchase rate (user sell-back), integer minor units (kopiykas) per gram. */
    @Field("purchaseRateMinor")
    private long purchaseRateMinor;

    /** Bank sale rate (user acquisition), integer minor units (kopiykas) per gram. */
    @Field("saleRateMinor")
    private long saleRateMinor;

    /** Date this metal's quote was published by the source. */
    @Field("quotationDate")
    private LocalDate quotationDate;

    /** When the scraper fetched this record; set by the backend at ingest (UTC). */
    @Field("fetchedAt")
    private Instant fetchedAt;

    public MetalPrice() {
    }

    public MetalPrice(String metal,
                      String rateGroup,
                      String weightKey,
                      double weightGrams,
                      String currency,
                      long purchaseRateMinor,
                      long saleRateMinor,
                      LocalDate quotationDate,
                      Instant fetchedAt) {
        this.metal = metal;
        this.rateGroup = rateGroup;
        this.weightKey = weightKey;
        this.weightGrams = weightGrams;
        this.currency = currency;
        this.purchaseRateMinor = purchaseRateMinor;
        this.saleRateMinor = saleRateMinor;
        this.quotationDate = quotationDate;
        this.fetchedAt = fetchedAt;
        this.id = compositeId(metal, rateGroup, weightKey);
    }

    /** Deterministic composite key {@code "<METAL>:<rateGroup>:<weightKey>"} (one record per combination). */
    public static String compositeId(String metal, String rateGroup, String weightKey) {
        return metal + ":" + rateGroup + ":" + weightKey;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMetal() {
        return metal;
    }

    public void setMetal(String metal) {
        this.metal = metal;
    }

    public String getRateGroup() {
        return rateGroup;
    }

    public void setRateGroup(String rateGroup) {
        this.rateGroup = rateGroup;
    }

    public String getWeightKey() {
        return weightKey;
    }

    public void setWeightKey(String weightKey) {
        this.weightKey = weightKey;
    }

    public double getWeightGrams() {
        return weightGrams;
    }

    public void setWeightGrams(double weightGrams) {
        this.weightGrams = weightGrams;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getPurchaseRateMinor() {
        return purchaseRateMinor;
    }

    public void setPurchaseRateMinor(long purchaseRateMinor) {
        this.purchaseRateMinor = purchaseRateMinor;
    }

    public long getSaleRateMinor() {
        return saleRateMinor;
    }

    public void setSaleRateMinor(long saleRateMinor) {
        this.saleRateMinor = saleRateMinor;
    }

    public LocalDate getQuotationDate() {
        return quotationDate;
    }

    public void setQuotationDate(LocalDate quotationDate) {
        this.quotationDate = quotationDate;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
