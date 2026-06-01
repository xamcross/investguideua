package com.investguide.investment;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * A persisted investment search (SPECIFICATION §6 {@code searchRequests}, §4.2; ticket BE-S2).
 *
 * <p><b>Lifecycle / status guard:</b> inserted {@code pending} with {@code tokenSpent=true} after the
 * token debit (BE-S3 step 4). It then transitions to {@code completed} (options persisted, usage
 * recorded) or {@code failed}. The {@code (status, tokenSpent)} pair is the guard the
 * {@code TokenLedgerService} refund relies on (§7.3): a single conditional flip
 * {@code {status:"pending", tokenSpent:true} -> {status:"failed", tokenSpent:false}} makes the refund
 * idempotent under retries/crash-resume. {@code status} is stored as a plain string to match exactly
 * the literals the ledger guards on.
 *
 * <p><b>No TTL (§6, §4.4, AC #4):</b> there is deliberately no TTL index on this collection — history is
 * retained indefinitely. {@code userId} is indexed for the owner-scoped, newest-first history query
 * (BE-S8).
 */
@Document(collection = "searchRequests")
public class SearchRequest {

    /** {@code searchRequests.status} literals — kept identical to the ledger's status guards (§7.3). */
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    @Id
    private String id;

    @Indexed
    @Field("userId")
    private String userId;

    @Field("input")
    private SearchInput input;

    @Field("status")
    private String status;

    @Field("options")
    private List<InvestmentOption> options;

    @Field("llmUsage")
    private LlmUsage llmUsage;

    @Field("tokenSpent")
    private boolean tokenSpent;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    public SearchRequest() {
    }

    /** Factory for the initial {@code pending}, {@code tokenSpent=true} insert (BE-S3 step 4). */
    public static SearchRequest pending(String userId, SearchInput input) {
        SearchRequest r = new SearchRequest();
        r.userId = userId;
        r.input = input;
        r.status = STATUS_PENDING;
        r.tokenSpent = true;
        r.options = List.of();
        return r;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public SearchInput getInput() {
        return input;
    }

    public void setInput(SearchInput input) {
        this.input = input;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<InvestmentOption> getOptions() {
        return options;
    }

    public void setOptions(List<InvestmentOption> options) {
        this.options = options;
    }

    public LlmUsage getLlmUsage() {
        return llmUsage;
    }

    public void setLlmUsage(LlmUsage llmUsage) {
        this.llmUsage = llmUsage;
    }

    public boolean isTokenSpent() {
        return tokenSpent;
    }

    public void setTokenSpent(boolean tokenSpent) {
        this.tokenSpent = tokenSpent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
