package com.investguide.news;

import com.investguide.config.NewsProperties;
import com.investguide.news.dto.NewsItemResponse;
import com.investguide.news.dto.NewsLatestResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Public financial-news read (feature 016, FR-4). Serves ONLY stored data -- the response time is a
 * Mongo read; {@link NewsRefreshService#maybeRefreshAsync()} refreshes in the background when the
 * store is stale. Empty collection returns empty lists, never an error. Anonymous route: listed in
 * the security {@code PUBLIC_GET} set.
 */
@RestController
@RequestMapping("/api/v1")
public class NewsController {

    private final NewsItemRepository repository;
    private final NewsRefreshService refreshService;
    private final NewsProperties properties;

    public NewsController(NewsItemRepository repository,
                          NewsRefreshService refreshService,
                          NewsProperties properties) {
        this.repository = repository;
        this.refreshService = refreshService;
        this.properties = properties;
    }

    @GetMapping("/news/latest")
    public ResponseEntity<NewsLatestResponse> latestNews() {
        refreshService.maybeRefreshAsync();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new NewsLatestResponse(list(NewsCategory.UKRAINE), list(NewsCategory.WORLD)));
    }

    private List<NewsItemResponse> list(NewsCategory category) {
        return repository
                .findByCategoryOrderByPublishedAtDesc(category, PageRequest.of(0, properties.servePerCategory()))
                .stream().map(NewsItemResponse::from).toList();
    }
}
