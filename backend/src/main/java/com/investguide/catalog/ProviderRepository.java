package com.investguide.catalog;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data repository for {@link Provider} (ticket BE-C1).
 *
 * <p>{@link #findByActiveTrue()} backs both the {@code GET /providers} transparency endpoint
 * (BE-C2) and the pre-prompt filtering helper (BE-C3) — inactive providers are never exposed to
 * clients nor sent to the LLM.
 */
public interface ProviderRepository extends MongoRepository<Provider, String> {

    List<Provider> findByActiveTrue();
}
