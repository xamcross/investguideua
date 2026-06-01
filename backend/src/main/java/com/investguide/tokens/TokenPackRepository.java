package com.investguide.tokens;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data repository for {@link TokenPack} (ticket BE-T1).
 *
 * <p>{@link #findByActiveTrue()} backs {@code GET /tokens/packs} (only active packs are
 * purchasable) and is also the set X7's seed-time pricing validation guards.
 */
public interface TokenPackRepository extends MongoRepository<TokenPack, String> {

    List<TokenPack> findByActiveTrue();
}
