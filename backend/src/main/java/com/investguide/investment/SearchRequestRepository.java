package com.investguide.investment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link SearchRequest} (tickets BE-S2, BE-S8).
 *
 * <p>Both finders are owner-scoped so a user can only ever read their own history (§4.4, §5.1):
 * {@link #findByUserIdOrderByCreatedAtDesc(String, Pageable)} backs the paginated, newest-first
 * history list; {@link #findByIdAndUserId(String, String)} backs single-search retrieval and returns
 * empty for a non-owned id so the controller can {@code 404} without revealing existence.
 */
public interface SearchRequestRepository extends MongoRepository<SearchRequest, String> {

    Page<SearchRequest> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<SearchRequest> findByIdAndUserId(String id, String userId);
}
