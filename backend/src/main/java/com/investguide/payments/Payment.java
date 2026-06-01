package com.investguide.payments;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * A token-pack purchase (SPECIFICATION §6 {@code payments}, §9; ticket BE-P1).
 *
 * <p>The {@code orderId} is the unique idempotency key (monobank {@code merchantPaymInfo.reference})
 * and is the join key the
 * {@link com.investguide.tokens.TokenLedgerService} status guards key on (§7.4, §7.5). The pack's
 * economics ({@code amountMinorUnits}, {@code currency}, {@code tokensToCredit}) are <strong>snapshotted
 * at creation time</strong> so a pack repricing or deactivation between create and callback never
 * changes the honored amount (§9.3).
 *
 * <p><b>Money is integer minor units only.</b> {@code amountMinorUnits} is a {@code long} of kopiykas;
 * there is no floating-point money field on this type. {@code status} is stored as the exact lowercase
 * string the ledger guards match ({@code pending|success|failed|reversed}) — see {@link PaymentStatus}.
 */
@Document(collection = "payments")
public class Payment {

    @Id
    private String id;

    @Indexed
    @Field("userId")
    private String userId;

    @Field("packId")
    private String packId;

    /** Unique idempotency key (unique index ensured in {@link PaymentIndexConfig}). */
    @Indexed(unique = true)
    @Field("orderId")
    private String orderId;

    /** Snapshot of the pack price in minor units (kopiykas). Integer only — never a float. */
    @Field("amountMinorUnits")
    private long amountMinorUnits;

    @Field("currency")
    private String currency;

    /**
     * Gateway-owned invoice id (monobank {@code invoiceId}), set after {@code createCheckout} returns.
     * Null until then and on any legacy row — so uniqueness is enforced by a <em>partial</em> index
     * ({@link PaymentIndexConfig}), never a plain unique index (which would collide on null).
     */
    @Field("providerInvoiceId")
    private String providerInvoiceId;

    /** Snapshot of the tokens this payment grants on a verified success (§9.3). */
    @Field("tokensToCredit")
    private int tokensToCredit;

    /** One of {@link PaymentStatus} ({@code pending|success|failed|reversed}). */
    @Field("status")
    private String status;

    /** Gateway slug — {@code monobank} for the MVP (swappable via {@link PaymentGateway}). */
    @Field("gateway")
    private String gateway;

    /** Raw verified callback payload, persisted for audit (§9.3). */
    @Field("gatewayPayload")
    private String gatewayPayload;

    /**
     * The {@code modifiedDate} of the last processed webhook — a low-water-mark used to ignore
     * out-of-order/stale webhooks (mono gives no delivery-order guarantee, §9.3). Advanced via a
     * guarded single-doc update, never a read-modify-write.
     */
    @Field("gatewayModifiedAt")
    private Instant gatewayModifiedAt;

    @Field("createdAt")
    private Instant createdAt;

    @Field("updatedAt")
    private Instant updatedAt;

    public Payment() {
    }

    /** Factory for a freshly created {@code pending} payment (BE-P2). */
    public static Payment pending(String userId, String packId, String orderId,
                                  long amountMinorUnits, String currency, int tokensToCredit,
                                  String gateway) {
        Payment p = new Payment();
        p.userId = userId;
        p.packId = packId;
        p.orderId = orderId;
        p.amountMinorUnits = amountMinorUnits;
        p.currency = currency;
        p.tokensToCredit = tokensToCredit;
        p.status = PaymentStatus.PENDING;
        p.gateway = gateway;
        Instant now = Instant.now();
        p.createdAt = now;
        p.updatedAt = now;
        return p;
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

    public String getPackId() {
        return packId;
    }

    public void setPackId(String packId) {
        this.packId = packId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public long getAmountMinorUnits() {
        return amountMinorUnits;
    }

    public void setAmountMinorUnits(long amountMinorUnits) {
        this.amountMinorUnits = amountMinorUnits;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getProviderInvoiceId() {
        return providerInvoiceId;
    }

    public void setProviderInvoiceId(String providerInvoiceId) {
        this.providerInvoiceId = providerInvoiceId;
    }

    public int getTokensToCredit() {
        return tokensToCredit;
    }

    public void setTokensToCredit(int tokensToCredit) {
        this.tokensToCredit = tokensToCredit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getGatewayPayload() {
        return gatewayPayload;
    }

    public void setGatewayPayload(String gatewayPayload) {
        this.gatewayPayload = gatewayPayload;
    }

    public Instant getGatewayModifiedAt() {
        return gatewayModifiedAt;
    }

    public void setGatewayModifiedAt(Instant gatewayModifiedAt) {
        this.gatewayModifiedAt = gatewayModifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
