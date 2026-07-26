package com.investguide.news;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NewsItemRepository extends MongoRepository<NewsItem, String> {

    List<NewsItem> findByCategoryOrderByPublishedAtDesc(NewsCategory category, Pageable pageable);

    /** Newest fetch stamp across both categories -- the staleness probe (FR-3). */
    Optional<NewsItem> findFirstByOrderByFetchedAtDesc();

    List<NewsItem> findByCategory(NewsCategory category);
}
