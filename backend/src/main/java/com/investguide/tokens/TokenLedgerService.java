package com.investguide.tokens;

import com.investguide.user.User;
import com.mongodb.client.result.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * The single source of truth for every {@code tokenBalance} mutation (ticket BE-T2,
 * SPECIFICATION §7). Centralising balance changes here means the money-correctness invariants
 * live in exactly one auditable place: <strong>no other class may {@code $inc} {@code tokenBalance}</strong>
 * (enforced by review/grep per the BE-T2 DoD).
 *
 * <p>Every method is one (or two deliberately ordered) <em>single-document conditional update(s)</em>
 * — never a read-modify-write, never a multi-document transaction (§7.6). Correctness comes from the
 * status/balance <em>guards</em> in the query, which make each operation idempotent under replay and
 * safe under concurrency on a standalone MongoDB:
 * <ul>
 *   <li><b>Debit</b> is guarded by {@code tokenBalance >= 1}, so it can never drive the balance
 *       below {@code 0} (§7.2, §7.7).</li>
 *   <li><b>Refund / credit / reversal</b> are each guarded by the originating document's status, so a
 *       replayed call matches {@code 0} documents and credits nothing (§7.3, §7.4, §7.5).</li>
 * </ul>
 *
 * <p>The {@code searchRequests} and {@code payments} status flips are expressed against the
 * collections by name so this service stays decoupled from the not-yet-built BE-S2 / BE-P1 entity
 * types while still owning the guarded balance mutation that must accompany each flip.
 */
@Service
public class TokenLedgerService {

    private static final Logger log = LoggerFactory.getLogger(TokenLedgerService.class);

    /** §6 collection names. {@code users} is addressed via {@link User} for type safety. */
    private static final String SEARCH_REQUESTS = "searchRequests";
    private static final String PAYMENTS = "payments";

    /** Bound on optimistic-retry attempts for the §7.5 reversal debit before accepting residual loss. */
    private static final int REVERSAL_MAX_ATTEMPTS = 8;

    private final MongoTemplate mongoTemplate;

    public TokenLedgerService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // ---- BE-A3: idempotent free-token grant (§4.1.2, §7) ---------------------------------

    /**
     * Atomically flip {@code emailVerified false -> true} and credit {@code n} free tokens in a
     * single guarded update. The {@code emailVerified == false} guard means concurrent or replayed
     * verifications credit the free tokens <strong>at most once</strong>: the first call matches one
     * document and grants; every later call matches zero and is a no-op (§4.1, AC #1).
     *
     * @return {@code true} if this call performed the flip+grant; {@code false} if the user was
     *         already verified (no tokens granted — caller treats this as a benign "already verified").
     */
    public boolean grantFreeTokens(String userId, int n) {
        Query query = Query.query(Criteria.where("_id").is(userId).and("emailVerified").is(false));
        Update update = new Update()
                .set("emailVerified", true)
                .inc("tokenBalance", n)
                .set("updatedAt", Instant.now());
        UpdateResult result = mongoTemplate.updateFirst(query, update, User.class);
        boolean granted = result.getModifiedCount() == 1;
        if (granted) {
            log.info("free_tokens_granted userId={} tokens={}", userId, n);
        }
        return granted;
    }

    // ---- BE-S3: token spend (§7.1, §7.2) -------------------------------------------------

    /**
     * Guarded single-token debit (§7.2): {@code updateOne({_id, tokenBalance:{$gte:1}}, {$inc:{tokenBalance:-1}})}.
     * The {@code >= 1} guard is what keeps the balance non-negative (§7.7) and makes concurrent
     * double-debits safe — at most {@code tokenBalance} of N racing debits can match.
     *
     * @return {@code true} if a token was debited; {@code false} if the user had no tokens — the
     *         caller maps {@code false} to {@code 402 INSUFFICIENT_TOKENS} and makes no LLM call (AC #2).
     */
    public boolean tryDebitOne(String userId) {
        Query query = Query.query(Criteria.where("_id").is(userId).and("tokenBalance").gte(1));
        Update update = new Update().inc("tokenBalance", -1).set("updatedAt", Instant.now());
        UpdateResult result = mongoTemplate.updateFirst(query, update, User.class);
        return result.getModifiedCount() == 1;
    }

    // ---- BE-S3 step 8: refund a post-insert search failure (§7.3) ------------------------

    /**
     * Status-guarded refund for a search that was debited and persisted {@code pending} but then
     * failed in the LLM/validation stage (§7.3, §4.2.6). Flips the {@code searchRequest} first:
     * {@code {_id:reqId, status:"pending", tokenSpent:true} -> {status:"failed", tokenSpent:false}};
     * <strong>only if that matched exactly one document</strong> is the user credited {@code +1}.
     * A concurrent retry or crash-resume matches zero documents and credits nothing, so the token is
     * refunded at most once (AC #3).
     *
     * <p>Use this only on the post-insert path. When the debit succeeded but the {@code SearchRequest}
     * insert itself failed there is no document to guard against — use {@link #refundForInsertFailure}.
     *
     * @return {@code true} if this call performed the refund; {@code false} if already refunded/replayed.
     */
    public boolean refundForFailedSearch(String reqId, String userId) {
        Query guard = Query.query(Criteria.where("_id").is(reqId)
                .and("status").is("pending")
                .and("tokenSpent").is(true));
        Update flip = new Update().set("status", "failed").set("tokenSpent", false);
        UpdateResult flipped = mongoTemplate.updateFirst(guard, flip, SEARCH_REQUESTS);
        if (flipped.getModifiedCount() != 1) {
            return false; // already failed/refunded, or never pending — credit nothing.
        }
        creditUnconditional(userId, 1);
        log.info("search_token_refunded reqId={} userId={}", reqId, userId);
        return true;
    }

    // ---- BE-S3 step 4: compensating refund when the insert itself failed (§4.2.3) --------

    /**
     * Compensating <strong>unguarded</strong> {@code +1} for the §4.2(3) case where {@code tryDebitOne}
     * succeeded but the {@code SearchRequest} insert failed, so <em>no document exists</em> to
     * status-guard against. The §7.3 guard cannot match a never-inserted doc, so reusing it here would
     * silently lose the token. The orchestrator (BE-S3 step 4) MUST call this exactly once on insert
     * failure and then abort — never on the post-insert path (use {@link #refundForFailedSearch} there).
     */
    public void refundForInsertFailure(String userId) {
        creditUnconditional(userId, 1);
        log.info("search_insert_failure_refunded userId={}", userId);
    }

    // ---- BE-S3: refund a search with no catalog match (BE-C3) ----------------------------

    /**
     * Status-guarded refund for a search whose pre-prompt catalog filter matched no provider (BE-C3):
     * no LLM call was made and no value was delivered, so the debited token is returned. Unlike a
     * failure, a no-match is a legitimate <em>completed</em> search, so this flips the {@code searchRequest}
     * {@code {status:"pending", tokenSpent:true} -> {status:"completed", tokenSpent:false}}; <strong>only
     * if that matched exactly one document</strong> is the user credited {@code +1}. Idempotent under
     * replay/crash-resume like the other guarded refunds — a second call matches zero documents and
     * credits nothing.
     *
     * @return {@code true} if this call performed the refund; {@code false} if already settled/replayed.
     */
    public boolean refundForNoMatch(String reqId, String userId) {
        Query guard = Query.query(Criteria.where("_id").is(reqId)
                .and("status").is("pending")
                .and("tokenSpent").is(true));
        Update flip = new Update().set("status", "completed").set("tokenSpent", false);
        UpdateResult flipped = mongoTemplate.updateFirst(guard, flip, SEARCH_REQUESTS);
        if (flipped.getModifiedCount() != 1) {
            return false; // already settled, or never pending — credit nothing.
        }
        creditUnconditional(userId, 1);
        log.info("search_no_match_refunded reqId={} userId={}", reqId, userId);
        return true;
    }

    // ---- BE-P4: credit a verified payment (§7.4) -----------------------------------------

    /**
     * Status-guarded crediting for a signature-verified {@code success} callback (§7.4). Flips the
     * payment {@code {orderId, status:"pending"} -> success}; <strong>only if that matched exactly one
     * document</strong> is the user credited {@code +tokens}. Idempotent per {@code orderId}: a replayed
     * callback matches zero documents and credits nothing extra (AC #6).
     *
     * @return {@code true} if this call credited the user; {@code false} on replay / non-pending payment.
     */
    public boolean creditFromPayment(String orderId, String userId, int tokens) {
        Query guard = Query.query(Criteria.where("orderId").is(orderId).and("status").is("pending"));
        Update flip = new Update().set("status", "success").set("updatedAt", Instant.now());
        UpdateResult flipped = mongoTemplate.updateFirst(guard, flip, PAYMENTS);
        if (flipped.getModifiedCount() != 1) {
            return false; // already credited or not pending — no double credit.
        }
        creditUnconditional(userId, tokens);
        log.info("payment_credited orderId={} userId={} tokens={}", orderId, userId, tokens);
        return true;
    }

    // ---- BE-P6: reversal / chargeback claw-back (§7.5) -----------------------------------

    /**
     * Reversal / chargeback handling (§7.5), runs at most once per {@code orderId}. First flips the
     * payment {@code {orderId, status:"success"} -> reversed} with a status guard; <strong>only if that
     * matched exactly one document</strong> does it claw back tokens. It then reads the payment's
     * snapshotted {@code tokensToCredit} and applies an optimistic, conditional debit of
     * {@code min(tokensToCredit, currentBalance)} — retried on a balance mismatch. The floor at the
     * remaining balance means the balance is never driven negative (§7.7); any shortfall (tokens already
     * spent before the chargeback) is an accepted MVP residual loss. A second reversal for the same
     * {@code orderId} matches zero documents on the flip and is a no-op (idempotent).
     *
     * @return {@code true} if this call performed the reversal flip; {@code false} on replay / not-success.
     */
    public boolean reversePayment(String orderId, String userId) {
        Query guard = Query.query(Criteria.where("orderId").is(orderId).and("status").is("success"));
        Update flip = new Update().set("status", "reversed").set("updatedAt", Instant.now());
        UpdateResult flipped = mongoTemplate.updateFirst(guard, flip, PAYMENTS);
        if (flipped.getModifiedCount() != 1) {
            return false; // not a success payment, or already reversed — idempotent no-op.
        }

        int tokensToCredit = readTokensToCredit(orderId);
        int debited = clawBackOptimistically(userId, tokensToCredit);
        log.info("payment_reversed orderId={} userId={} tokensToCredit={} debited={}",
                orderId, userId, tokensToCredit, debited);
        return true;
    }

    // ---- internals -----------------------------------------------------------------------

    /** Unconditional {@code +amount} credit. Callers above gate this behind a single-doc status guard. */
    private void creditUnconditional(String userId, int amount) {
        Query query = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update().inc("tokenBalance", amount).set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, User.class);
    }

    /** Read the payment's snapshotted {@code tokensToCredit}; {@code 0} if the payment/field is absent. */
    private int readTokensToCredit(String orderId) {
        Query query = Query.query(Criteria.where("orderId").is(orderId));
        query.fields().include("tokensToCredit");
        org.bson.Document doc = mongoTemplate.findOne(query, org.bson.Document.class, PAYMENTS);
        if (doc == null) {
            return 0;
        }
        Number tokens = doc.get("tokensToCredit", Number.class);
        return tokens == null ? 0 : tokens.intValue();
    }

    /**
     * Optimistic claw-back (§7.5): read the current balance, debit {@code min(tokensToCredit, balance)}
     * with a conditional update keyed on the exact balance read, retry on mismatch. Floors at the
     * remaining balance so the result is never negative.
     *
     * @return the number of tokens actually debited.
     */
    private int clawBackOptimistically(String userId, int tokensToCredit) {
        if (tokensToCredit <= 0) {
            return 0;
        }
        for (int attempt = 0; attempt < REVERSAL_MAX_ATTEMPTS; attempt++) {
            User user = mongoTemplate.findById(userId, User.class);
            if (user == null) {
                return 0;
            }
            int balance = user.getTokenBalance();
            int debit = Math.min(tokensToCredit, balance);
            if (debit <= 0) {
                return 0; // nothing left to claw back.
            }
            Query conditional = Query.query(Criteria.where("_id").is(userId).and("tokenBalance").is(balance));
            Update update = new Update().inc("tokenBalance", -debit).set("updatedAt", Instant.now());
            UpdateResult result = mongoTemplate.updateFirst(conditional, update, User.class);
            if (result.getModifiedCount() == 1) {
                return debit;
            }
            // Balance changed under us between read and write — retry with a fresh read.
        }
        log.warn("payment_reversal_contended userId={} tokensToCredit={} attempts={} - residual loss accepted",
                userId, tokensToCredit, REVERSAL_MAX_ATTEMPTS);
        return 0;
    }
}
