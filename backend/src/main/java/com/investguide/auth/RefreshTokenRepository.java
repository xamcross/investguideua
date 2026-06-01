package com.investguide.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repository for {@link RefreshToken} (ticket BE-A4). Lookups are by the SHA-256 hash of the
 * presented refresh token; raw tokens are never persisted.
 */
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
