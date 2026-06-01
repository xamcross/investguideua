package com.investguide.tokens;

import com.investguide.user.User;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TokenLedgerService} — the single source of truth for balance mutations
 * (ticket BE-T2, SPECIFICATION §7; QA1 ledger concurrency/idempotency tests). MongoTemplate is
 * mocked so these run without a live Mongo; the guarded-query construction and the
 * matched-count-driven branching (the actual correctness logic) are asserted directly.
 *
 * <p>Match counts are simulated with {@link UpdateResult#acknowledged(long, long, org.bson.BsonValue)}:
 * {@code modifiedCount == 1} means the guard matched (the operation took effect); {@code 0} means it
 * did not (replay / insufficient balance / wrong status), which is exactly the signal each method
 * branches on.
 */
class TokenLedgerServiceTest {

    private static final String SEARCH_REQUESTS = "searchRequests";
    private static final String PAYMENTS = "payments";

    private MongoTemplate mongoTemplate;
    private TokenLedgerService ledger;

    private static UpdateResult result(long matched, long modified) {
        return UpdateResult.acknowledged(matched, modified, null);
    }

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        ledger = new TokenLedgerService(mongoTemplate);
    }

    // ---- grantFreeTokens (§4.1, AC #1) ---------------------------------------------------

    @Test
    void grantFreeTokens_firstVerification_flipsGuardAndCredits_returnsTrue() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(1, 1));

        boolean granted = ledger.grantFreeTokens("u1", 5);

        assertThat(granted).isTrue();
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(q.capture(), u.capture(), eq(User.class));
        // Guard: only an as-yet-unverified user matches, so the grant happens at most once.
        Document query = q.getValue().getQueryObject();
        assertThat(query.get("_id")).isEqualTo("u1");
        assertThat(query.get("emailVerified")).isEqualTo(false);
        Document update = u.getValue().getUpdateObject();
        assertThat(((Document) update.get("$set")).get("emailVerified")).isEqualTo(true);
        assertThat(((Document) update.get("$inc")).get("tokenBalance")).isEqualTo(5);
    }

    @Test
    void grantFreeTokens_replay_matchesNoDoc_returnsFalse_noRegrant() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(0, 0));

        assertThat(ledger.grantFreeTokens("u1", 5)).isFalse();
        // Exactly one conditional update was attempted; nothing else mutates the balance.
        verify(mongoTemplate, times(1))
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
        verifyNoMoreInteractions(mongoTemplate);
    }

    // ---- tryDebitOne (§7.2, §7.7, AC #2) -------------------------------------------------

    @Test
    void tryDebitOne_withBalance_debits_returnsTrue_andGuardsOnGte1() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(1, 1));

        boolean debited = ledger.tryDebitOne("u1");

        assertThat(debited).isTrue();
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(q.capture(), u.capture(), eq(User.class));
        // The {tokenBalance >= 1} guard is what prevents the balance going negative under races.
        Document balanceGuard = (Document) q.getValue().getQueryObject().get("tokenBalance");
        assertThat(balanceGuard.get("$gte")).isEqualTo(1);
        assertThat(((Document) u.getValue().getUpdateObject().get("$inc")).get("tokenBalance"))
                .isEqualTo(-1);
    }

    @Test
    void tryDebitOne_zeroBalance_matchesNoDoc_returnsFalse() {
        // Simulates balance 0 (and concurrent double-debit beyond available): guard matches nothing,
        // so balance can never be driven below 0 (§7.7) and the caller returns 402 with no LLM call.
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(0, 0));

        assertThat(ledger.tryDebitOne("u1")).isFalse();
    }

    // ---- refundForFailedSearch (§7.3, AC #3) ---------------------------------------------

    @Test
    void refundForFailedSearch_firstTime_flipsThenCredits_returnsTrue() {
        // searchRequests status-guard matches 1 -> proceed to credit.
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(SEARCH_REQUESTS)))
                .thenReturn(result(1, 1));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(1, 1));

        boolean refunded = ledger.refundForFailedSearch("req1", "u1");

        assertThat(refunded).isTrue();
        // Verify the status guard on the searchRequest flip.
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(q.capture(), any(UpdateDefinition.class), eq(SEARCH_REQUESTS));
        Document guard = q.getValue().getQueryObject();
        assertThat(guard.get("_id")).isEqualTo("req1");
        assertThat(guard.get("status")).isEqualTo("pending");
        assertThat(guard.get("tokenSpent")).isEqualTo(true);
        // And that the credit (+1) was applied to the user exactly once.
        ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), u.capture(), eq(User.class));
        assertThat(((Document) u.getValue().getUpdateObject().get("$inc")).get("tokenBalance"))
                .isEqualTo(1);
    }

    @Test
    void refundForFailedSearch_replay_guardMatchesNoDoc_creditsNothing_returnsFalse() {
        // Second/replayed refund: the searchRequest is no longer pending -> match 0 -> no credit (AC #3).
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(SEARCH_REQUESTS)))
                .thenReturn(result(0, 0));

        boolean refunded = ledger.refundForFailedSearch("req1", "u1");

        assertThat(refunded).isFalse();
        // Critically: no balance credit on the user when the guard did not match.
        verify(mongoTemplate, never())
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
    }

    // ---- refundForNoMatch (BE-C3) --------------------------------------------------------

    @Test
    void refundForNoMatch_firstTime_flipsToCompletedThenCredits_returnsTrue() {
        // searchRequests status-guard matches 1 -> proceed to credit.
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(SEARCH_REQUESTS)))
                .thenReturn(result(1, 1));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(1, 1));

        boolean refunded = ledger.refundForNoMatch("req1", "u1");

        assertThat(refunded).isTrue();
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(q.capture(), u.capture(), eq(SEARCH_REQUESTS));
        Document guard = q.getValue().getQueryObject();
        assertThat(guard.get("_id")).isEqualTo("req1");
        assertThat(guard.get("status")).isEqualTo("pending");
        assertThat(guard.get("tokenSpent")).isEqualTo(true);
        // A no-match is a COMPLETED (not failed) search; tokenSpent is cleared.
        Document set = (Document) u.getValue().getUpdateObject().get("$set");
        assertThat(set.get("status")).isEqualTo("completed");
        assertThat(set.get("tokenSpent")).isEqualTo(false);
        // And the +1 credit was applied to the user exactly once.
        ArgumentCaptor<UpdateDefinition> uu = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), uu.capture(), eq(User.class));
        assertThat(((Document) uu.getValue().getUpdateObject().get("$inc")).get("tokenBalance"))
                .isEqualTo(1);
    }

    @Test
    void refundForNoMatch_replay_guardMatchesNoDoc_creditsNothing_returnsFalse() {
        // Second/replayed refund: the searchRequest is no longer pending -> match 0 -> no credit.
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(SEARCH_REQUESTS)))
                .thenReturn(result(0, 0));

        assertThat(ledger.refundForNoMatch("req1", "u1")).isFalse();
        verify(mongoTemplate, never())
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
    }

    // ---- refundForInsertFailure (§4.2.3) -------------------------------------------------

    @Test
    void refundForInsertFailure_creditsUnconditionally() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(1, 1));

        ledger.refundForInsertFailure("u1");

        ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), u.capture(), eq(User.class));
        // Compensating +1: no status guard exists because no SearchRequest document was inserted.
        assertThat(((Document) u.getValue().getUpdateObject().get("$inc")).get("tokenBalance"))
                .isEqualTo(1);
        // No searchRequests collection touched on this path.
        verify(mongoTemplate, never())
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(SEARCH_REQUESTS));
    }

    // ---- creditFromPayment (§7.4, AC #6) -------------------------------------------------

    @Test
    void creditFromPayment_verifiedSuccess_creditsExactlyOnce_returnsTrue() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(PAYMENTS)))
                .thenReturn(result(1, 1));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(1, 1));

        boolean credited = ledger.creditFromPayment("order-1", "u1", 10);

        assertThat(credited).isTrue();
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(q.capture(), any(UpdateDefinition.class), eq(PAYMENTS));
        Document guard = q.getValue().getQueryObject();
        assertThat(guard.get("orderId")).isEqualTo("order-1");
        assertThat(guard.get("status")).isEqualTo("pending");
        ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), u.capture(), eq(User.class));
        assertThat(((Document) u.getValue().getUpdateObject().get("$inc")).get("tokenBalance"))
                .isEqualTo(10);
    }

    @Test
    void creditFromPayment_replayedCallback_creditsNothingExtra_returnsFalse() {
        // Replayed callback: payment already success -> {orderId, status:pending} matches 0 (AC #6).
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(PAYMENTS)))
                .thenReturn(result(0, 0));

        assertThat(ledger.creditFromPayment("order-1", "u1", 10)).isFalse();
        verify(mongoTemplate, never())
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
    }

    // ---- reversePayment (§7.5) -----------------------------------------------------------

    @Test
    void reversePayment_success_clawsBackTokens_flooredAtBalance() {
        // Flip {orderId, status:success} -> reversed matches 1, then claw back min(tokensToCredit, balance).
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(PAYMENTS)))
                .thenReturn(result(1, 1));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq(PAYMENTS)))
                .thenReturn(new Document("tokensToCredit", 10));
        User user = userWithBalance("u1", 4); // already spent 6 of 10 -> floor debit at 4
        when(mongoTemplate.findById("u1", User.class)).thenReturn(user);
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(1, 1));

        boolean reversed = ledger.reversePayment("order-1", "u1");

        assertThat(reversed).isTrue();
        ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), u.capture(), eq(User.class));
        // debit = min(10, 4) = 4; balance floored at 0, never negative (§7.7).
        assertThat(((Document) u.getValue().getUpdateObject().get("$inc")).get("tokenBalance"))
                .isEqualTo(-4);
    }

    @Test
    void reversePayment_replay_flipMatchesNoDoc_isNoOp_returnsFalse() {
        // Second reversal for the same orderId: payment already reversed -> flip matches 0 (idempotent).
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(PAYMENTS)))
                .thenReturn(result(0, 0));

        assertThat(ledger.reversePayment("order-1", "u1")).isFalse();
        verify(mongoTemplate, never()).findById(any(), eq(User.class));
        verify(mongoTemplate, never())
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
    }

    @Test
    void reversePayment_optimisticRetry_onBalanceMismatch_thenSucceeds() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(PAYMENTS)))
                .thenReturn(result(1, 1));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq(PAYMENTS)))
                .thenReturn(new Document("tokensToCredit", 5));
        // Balance read twice: the first conditional debit loses the race (modified 0), the second wins.
        when(mongoTemplate.findById("u1", User.class))
                .thenReturn(userWithBalance("u1", 8))
                .thenReturn(userWithBalance("u1", 7));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(0, 0))
                .thenReturn(result(1, 1));

        boolean reversed = ledger.reversePayment("order-1", "u1");

        assertThat(reversed).isTrue();
        verify(mongoTemplate, times(2)).findById("u1", User.class);
        verify(mongoTemplate, times(2))
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
    }

    @Test
    void reversePayment_zeroBalance_nothingToClawBack_returnsTrue() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(PAYMENTS)))
                .thenReturn(result(1, 1));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq(PAYMENTS)))
                .thenReturn(new Document("tokensToCredit", 10));
        when(mongoTemplate.findById("u1", User.class)).thenReturn(userWithBalance("u1", 0));

        boolean reversed = ledger.reversePayment("order-1", "u1");

        assertThat(reversed).isTrue();
        // debit = min(10, 0) = 0 -> no conditional debit issued; balance stays at 0 (never negative).
        verify(mongoTemplate, never())
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
    }

    @Test
    void reversePayment_missingPaymentDoc_clawsBackNothing_returnsTrue() {
        // Flip matched, but the payment doc can't be read back -> tokensToCredit defaults to 0,
        // so nothing is clawed back (no accidental debit on a missing/garbled payment).
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(PAYMENTS)))
                .thenReturn(result(1, 1));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq(PAYMENTS)))
                .thenReturn(null);

        assertThat(ledger.reversePayment("order-1", "u1")).isTrue();
        verify(mongoTemplate, never()).findById(any(), eq(User.class));
        verify(mongoTemplate, never())
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
    }

    @Test
    void reversePayment_retryExhaustion_acceptsResidualLoss_returnsTrue() {
        // Pathological contention: every optimistic debit loses the race. The loop is bounded
        // (REVERSAL_MAX_ATTEMPTS=8) and exits accepting residual loss rather than spinning forever.
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(PAYMENTS)))
                .thenReturn(result(1, 1));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq(PAYMENTS)))
                .thenReturn(new Document("tokensToCredit", 5));
        when(mongoTemplate.findById("u1", User.class)).thenReturn(userWithBalance("u1", 5));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(result(0, 0)); // never wins

        assertThat(ledger.reversePayment("order-1", "u1")).isTrue();
        // Bounded retries: read + conditional-debit attempted exactly REVERSAL_MAX_ATTEMPTS times.
        verify(mongoTemplate, times(8)).findById("u1", User.class);
        verify(mongoTemplate, times(8))
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
    }

    private static User userWithBalance(String id, int balance) {
        User u = new User();
        u.setId(id);
        u.setTokenBalance(balance);
        return u;
    }
}
