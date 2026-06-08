package com.investguide.metals;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link MetalPrice} (feature 011).
 *
 * <p>Keyed by the composite {@code _id} ({@code <METAL>:<rateGroup>:<weightKey>}). {@code save()}
 * therefore upserts per combination, which is exactly the latest-quote-only semantics the ingest needs.
 * {@code findAll()} backs the ADMIN read endpoint. There is deliberately no delete path: a partial or
 * failed run must never blank combinations absent from a batch (SC-006).
 *
 * <p>{@link #findFirstByMetalAndRateGroupOrderByWeightGramsAsc} returns the smallest weight tier of a
 * metal within a rate group - the canonical per-gram quote used to ground a precious-metals investment
 * option (FR-018).
 */
public interface MetalPriceRepository extends MongoRepository<MetalPrice, String> {

    Optional<MetalPrice> findFirstByMetalAndRateGroupOrderByWeightGramsAsc(String metal, String rateGroup);
}
