package com.investguide.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link User} (ticket BE-A1).
 *
 * <p>Callers MUST lowercase the email before calling {@link #findByEmail(String)}; emails are
 * persisted lowercased so this yields case-insensitive lookups consistent with the unique index.
 * All atomic {@code tokenBalance} mutations (including the verification flip+grant) live in
 * {@code com.investguide.tokens.TokenLedgerService} — the single source of truth (BE-T2) — so this
 * repository deliberately exposes no balance-mutating methods.
 */
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
