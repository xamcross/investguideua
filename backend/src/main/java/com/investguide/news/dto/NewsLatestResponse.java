package com.investguide.news.dto;

import java.util.List;

/** Response body of GET /api/v1/news/latest (feature 016, FR-4). */
public record NewsLatestResponse(List<NewsItemResponse> ukraine, List<NewsItemResponse> world) {
}
