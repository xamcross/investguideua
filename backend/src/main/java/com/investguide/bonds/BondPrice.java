package com.investguide.bonds;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Latest known quote for one bond instrument (feature 009 {@code bondPrices}).
 *
 * <p><b>Keying:</b> {@code isin} is the document {@code _id}. Uniqueness is therefore implicit (the
 * {@code _id} index always exists) and the ingest upsert-by-ISIN keeps exactly one record per
 * instrument; no {@code @Indexed(unique=true)} is needed on the id.
 *
 * <p><b>Money units (project rule):</b> {@code sellPriceMinor}/{@code buyPriceMinor} are integer
 * <b>minor units</b> (kopiykas for UAH, cents for USD/EUR) of {@link #currency}, quoted per 1000
 * face value and stored as-is (no rescaling) - never floats. {@code sellYield}/{@code buyYield} are
 * reference percentages, so they stay doubles.
 *
 * <p>{@code fetchedAt} is stamped server-side at ingest (a single instant per batch) so an admin can
 * judge freshness even if a daily run was missed.
 */
@Document(collection = "bondPrices")
public class BondPrice {

    @Id
    private String isin;

    @Field("military")
    private boolean military;

    /** Quote currency: {@code UAH}, {@code USD}, or {@code EUR}. */
    @Field("currency")
    private String currency;

    @Field("maturity")
    private LocalDate maturity;

    /** Date the source quoted these prices. */
    @Field("quotationDate")
    private LocalDate quotationDate;

    /** Sell price per 1000 face value, integer minor units of {@link #currency}. */
    @Field("sellPriceMinor")
    private long sellPriceMinor;

    /** Buy price per 1000 face value, integer minor units of {@link #currency}. */
    @Field("buyPriceMinor")
    private long buyPriceMinor;

    @Field("sellYield")
    private double sellYield;

    @Field("buyYield")
    private double buyYield;

    /** When the scraper fetched this record; set by the backend at ingest (UTC). */
    @Field("fetchedAt")
    private Instant fetchedAt;

    public BondPrice() {
    }

    public BondPrice(String isin,
                     boolean military,
                     String currency,
                     LocalDate maturity,
                     LocalDate quotationDate,
                     long sellPriceMinor,
                     long buyPriceMinor,
                     double sellYield,
                     double buyYield,
                     Instant fetchedAt) {
        this.isin = isin;
        this.military = military;
        this.currency = currency;
        this.maturity = maturity;
        this.quotationDate = quotationDate;
        this.sellPriceMinor = sellPriceMinor;
        this.buyPriceMinor = buyPriceMinor;
        this.sellYield = sellYield;
        this.buyYield = buyYield;
        this.fetchedAt = fetchedAt;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public boolean isMilitary() {
        return military;
    }

    public void setMilitary(boolean military) {
        this.military = military;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getMaturity() {
        return maturity;
    }

    public void setMaturity(LocalDate maturity) {
        this.maturity = maturity;
    }

    public LocalDate getQuotationDate() {
        return quotationDate;
    }

    public void setQuotationDate(LocalDate quotationDate) {
        this.quotationDate = quotationDate;
    }

    public long getSellPriceMinor() {
        return sellPriceMinor;
    }

    public void setSellPriceMinor(long sellPriceMinor) {
        this.sellPriceMinor = sellPriceMinor;
    }

    public long getBuyPriceMinor() {
        return buyPriceMinor;
    }

    public void setBuyPriceMinor(long buyPriceMinor) {
        this.buyPriceMinor = buyPriceMinor;
    }

    public double getSellYield() {
        return sellYield;
    }

    public void setSellYield(double sellYield) {
        this.sellYield = sellYield;
    }

    public double getBuyYield() {
        return buyYield;
    }

    public void setBuyYield(double buyYield) {
        this.buyYield = buyYield;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
