package com.investguide.bonds;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data repository for {@link BondPrice} (feature 009).
 *
 * <p>Keyed by ISIN ({@code _id}). {@code save()} therefore upserts by instrument - inserting a new
 * ISIN or overwriting an existing one - which is exactly the latest-quote-only semantics the ingest
 * needs. {@code findAll()} backs the ADMIN read endpoint. There is deliberately no delete path: a
 * partial or failed run must never blank instruments absent from a batch (SC-006).
 */
public interface BondPriceRepository extends MongoRepository<BondPrice, String> {
}
