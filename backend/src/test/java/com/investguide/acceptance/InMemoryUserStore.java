package com.investguide.acceptance;

import com.investguide.user.User;
import com.investguide.user.UserRepository;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A minimal in-memory implementation of the exact {@code users}-collection operations that
 * {@link com.investguide.tokens.TokenLedgerService} performs, so the QA1 acceptance suite can assert
 * <em>real balance arithmetic</em> (AC #1 "0 -> 5 -> 4", AC #3 "unchanged after refund", AC #6 "credited
 * exactly once") through the real ledger rather than asserting on mock interactions alone.
 *
 * <p>It interprets the ledger's guarded single-document updates faithfully: it honours the
 * {@code emailVerified == false} grant guard (so a re-verify credits nothing), the
 * {@code tokenBalance >= 1} debit guard (so the balance can never go negative, §7.7), and the exact
 * {@code tokenBalance == n} reversal guard, applying the {@code $inc}/{@code $set} only when the guard
 * matches. This mirrors what a standalone MongoDB would do for these conditional updates.
 *
 * <p>{@link #template()} and {@link #repository()} return Mockito mocks wired to the same backing map,
 * so a read via {@link UserRepository#findById} reflects mutations applied via {@link MongoTemplate}.
 */
final class InMemoryUserStore {

    private final Map<String, User> users = new HashMap<>();
    private final MongoTemplate template = mock(MongoTemplate.class);
    private final UserRepository repository = mock(UserRepository.class);

    InMemoryUserStore() {
        wireTemplate();
        wireRepository();
    }

    /** Seed a user with an explicit verification state and starting balance. */
    User put(String id, String email, boolean emailVerified, int balance) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setEmailVerified(emailVerified);
        u.setTokenBalance(balance);
        users.put(id, u);
        return u;
    }

    int balanceOf(String id) {
        User u = users.get(id);
        return u == null ? -1 : u.getTokenBalance();
    }

    boolean isVerified(String id) {
        User u = users.get(id);
        return u != null && u.isEmailVerified();
    }

    MongoTemplate template() {
        return template;
    }

    UserRepository repository() {
        return repository;
    }

    private void wireRepository() {
        when(repository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(users.get(inv.<String>getArgument(0))));
    }

    private void wireTemplate() {
        when(template.findById(anyString(), eq(User.class)))
                .thenAnswer(inv -> users.get(inv.<String>getArgument(0)));

        when(template.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenAnswer(inv -> applyUserUpdate(inv.getArgument(0), inv.getArgument(1)));
    }

    /** Evaluate the guards against the addressed user; apply $inc/$set only on a match. */
    private UpdateResult applyUserUpdate(Query query, UpdateDefinition update) {
        Document q = query.getQueryObject();
        Document u = update.getUpdateObject();

        Object id = q.get("_id");
        User user = id == null ? null : users.get(id.toString());
        if (user == null || !guardsMatch(user, q)) {
            return UpdateResult.acknowledged(0, 0L, null);
        }

        Document inc = (Document) u.get("$inc");
        if (inc != null && inc.containsKey("tokenBalance")) {
            user.setTokenBalance(user.getTokenBalance() + ((Number) inc.get("tokenBalance")).intValue());
        }
        Document set = (Document) u.get("$set");
        if (set != null && set.containsKey("emailVerified")) {
            user.setEmailVerified(Boolean.TRUE.equals(set.get("emailVerified")));
        }
        return UpdateResult.acknowledged(1, 1L, null);
    }

    private static boolean guardsMatch(User user, Document q) {
        if (q.containsKey("emailVerified")
                && user.isEmailVerified() != Boolean.TRUE.equals(q.get("emailVerified"))) {
            return false;
        }
        if (q.containsKey("tokenBalance")) {
            Object guard = q.get("tokenBalance");
            if (guard instanceof Document range) {
                if (range.containsKey("$gte")
                        && user.getTokenBalance() < ((Number) range.get("$gte")).intValue()) {
                    return false;
                }
            } else if (guard instanceof Number exact) {
                if (user.getTokenBalance() != exact.intValue()) {
                    return false;
                }
            }
        }
        return true;
    }
}
