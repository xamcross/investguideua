package com.investguide.news.dto;

import com.investguide.news.NewsItem;

import java.time.Instant;

/** Public headline row (feature 016, FR-4). No id/category/fetchedAt -- presentation needs only these. */
public record NewsItemResponse(String sourceName, String title, String link, Instant publishedAt) {

    public static NewsItemResponse from(NewsItem item) {
        return new NewsItemResponse(item.getSourceName(), item.getTitle(), item.getLink(),
                item.getPublishedAt());
    }
}
