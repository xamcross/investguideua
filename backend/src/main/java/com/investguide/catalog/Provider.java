package com.investguide.catalog;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * Provider catalog document (SPECIFICATION §6 {@code providers}; ticket BE-C1).
 *
 * <p>The catalog is the bounded universe of options the LLM is allowed to recommend (§8.3). It is
 * seed/DB-managed for the MVP (§3, §1.2 — no admin UI); see {@code ProviderSeeder} (X7).
 *
 * <p><b>Keying:</b> {@code _id} is a stable string slug (e.g. {@code "privatbank"}). The model
 * returns these slugs and they are enforced against the active catalog server-side (§8.3, BE-S6),
 * so the slug doubles as the recommendation identity — it must never be reused for a different
 * provider.
 *
 * <p><b>Money units (project rule):</b> {@code minAmount}/{@code maxAmount} are integer
 * <b>minor units</b> — never floats — denominated in the row's <b>quote currency</b>
 * ({@code currencies[0]}): a UAH row stores kopiykas, a USD/EUR row stores cents. A provider that
 * accepts "from 1,000 UAH" is stored as {@code minAmount = 100_000}; a USD broker "from $100" as
 * {@code 10_000}. Each row is currency-coherent (UAH-only or FX-only, never mixed) and the
 * pre-prompt filter (BE-C3) only keeps rows whose {@code currencies} include the requested currency,
 * so the {@code minAmount} comparison is always within one currency. {@code typicalReturnPct} holds
 * percentages (reference data, not money), so it uses a numeric range rather than minor units.
 *
 * <p>{@code active} is the only field an operator is expected to flip directly in the DB; the
 * seeder is insert-only (upsert-by-{@code _id} with {@code $setOnInsert}) and therefore never
 * clobbers a manually edited {@code active} flag on re-run (X7 DoD).
 */
@Document(collection = "providers")
public class Provider {

    @Id
    private String id;

    @Field("name")
    private String name;

    @Field("category")
    private ProviderCategory category;

    @Field("description")
    private String description;

    /** Minimum accepted amount, integer minor units (kopiykas/cents). {@code 0} means no minimum. */
    @Field("minAmount")
    private long minAmount;

    /** Maximum accepted amount, integer minor units; {@code null} means no upper limit. */
    @Field("maxAmount")
    private Long maxAmount;

    @Field("currencies")
    private List<String> currencies;

    @Field("typicalReturnPct")
    private ReturnRange typicalReturnPct;

    @Field("riskLevel")
    private RiskLevel riskLevel;

    @Field("sourceUrl")
    private String sourceUrl;

    /** Only {@code active=true} providers are listed (§5.1) and sent to the LLM (§8.3). */
    @Indexed
    @Field("active")
    private boolean active;

    public Provider() {
    }

    /** Full constructor used by the seeder (X7). */
    public Provider(String id,
                    String name,
                    ProviderCategory category,
                    String description,
                    long minAmount,
                    Long maxAmount,
                    List<String> currencies,
                    ReturnRange typicalReturnPct,
                    RiskLevel riskLevel,
                    String sourceUrl,
                    boolean active) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.currencies = currencies;
        this.typicalReturnPct = typicalReturnPct;
        this.riskLevel = riskLevel;
        this.sourceUrl = sourceUrl;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProviderCategory getCategory() {
        return category;
    }

    public void setCategory(ProviderCategory category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(long minAmount) {
        this.minAmount = minAmount;
    }

    public Long getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(Long maxAmount) {
        this.maxAmount = maxAmount;
    }

    public List<String> getCurrencies() {
        return currencies;
    }

    public void setCurrencies(List<String> currencies) {
        this.currencies = currencies;
    }

    public ReturnRange getTypicalReturnPct() {
        return typicalReturnPct;
    }

    public void setTypicalReturnPct(ReturnRange typicalReturnPct) {
        this.typicalReturnPct = typicalReturnPct;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
