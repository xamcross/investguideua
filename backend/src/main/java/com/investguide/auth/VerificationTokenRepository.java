package com.investguide.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repository for {@link VerificationToken} (ticket BE-A3). Lookups are by {@code tokenHash}
 * (the SHA-256 of the raw token presented by the user).
 */
public interface VerificationTokenRepository extends MongoRepository<VerificationToken, String> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);
}
