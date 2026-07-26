package com.investguide.news;

import com.investguide.config.NewsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stale-while-revalidate news refresh (feature 016, FR-3). Readers never wait: the controller
 * serves Mongo and calls {@link #maybeRefreshAsync()}, which no-ops unless the newest stored
 * {@code fetchedAt} is older than {@code news.refresh-ttl}. The in-flight guard makes concurrent
 * stale reads produce exactly one refresh job (single backend instance — an AtomicBoolean is
 * sufficient, no distributed lock needed). A volatile last-attempt stamp additionally caps retry
 * cadence to the TTL while feeds keep failing: a total feed outage under steady traffic must not
 * mean continuous outbound fetching.
 *
 * <p><b>Per-feed isolation:</b> each configured feed is fetched and parsed independently; a feed
 * failure is logged and skipped. <b>A failed or empty fetch never deletes stored items</b> — the
 * same invariant as metals ingest. Successful batches upsert by link-hash {@code _id}, then the
 * category is pruned to the newest {@code keep-per-category} by {@code publishedAt}.
 */
@Service
public class NewsRefreshService {

    private static final Logger log = LoggerFactory.getLogger(NewsRefreshService.class);

    private final NewsItemRepository repository;
    private final NewsFeedFetcher fetcher;
    private final NewsProperties properties;
    private final Executor executor;
    private final Clock clock;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private volatile Instant lastAttemptAt;

    @Autowired
    public NewsRefreshService(NewsItemRepository repository,
                              NewsFeedFetcher fetcher,
                              NewsProperties properties,
                              @Qualifier("newsRefreshExecutor") ExecutorService executor) {
        this(repository, fetcher, properties, (Executor) executor, Clock.systemUTC());
    }

    /** Test seam: direct executor + fixed clock keep refresh tests deterministic. */
    NewsRefreshService(NewsItemRepository repository,
                       NewsFeedFetcher fetcher,
                       NewsProperties properties,
                       Executor executor,
                       Clock clock) {
        this.repository = repository;
        this.fetcher = fetcher;
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
    }

    /** Serve-path hook: cheap when fresh, schedules at most one background refresh when stale. */
    public void maybeRefreshAsync() {
        if (!isStale()) {
            return;
        }
        if (!refreshInFlight.compareAndSet(false, true)) {
            return;
        }
        lastAttemptAt = clock.instant();
        try {
            executor.execute(() -> {
                try {
                    refresh();
                } catch (RuntimeException ex) {
                    log.warn("news_refresh crashed", ex);
                } finally {
                    refreshInFlight.set(false);
                }
            });
        } catch (RuntimeException ex) { // e.g. RejectedExecutionException on shutdown
            refreshInFlight.set(false);
            log.warn("news_refresh submission rejected", ex);
        }
    }

    private boolean isStale() {
        Instant cutoff = clock.instant().minus(properties.refreshTtl());
        Instant attempted = lastAttemptAt;
        if (attempted != null && !attempted.isBefore(cutoff)) {
            return false; // failure backoff: even with nothing stored, attempt at most once per TTL
        }
        return repository.findFirstByOrderByFetchedAtDesc()
                .map(item -> item.getFetchedAt().isBefore(cutoff))
                .orElse(true);
    }

    private void refresh() {
        Instant fetchedAt = clock.instant();
        int accepted = 0;
        int rejected = 0;
        int failedFeeds = 0;
        for (NewsCategory category : NewsCategory.values()) {
            List<NewsItem> items = new ArrayList<>();
            for (NewsProperties.Feed feed : properties.feedsFor(category)) {
                try {
                    NewsFeedParser.Result result = NewsFeedParser.parse(
                            fetcher.fetch(feed.url()), category, feed.sourceName(), fetchedAt);
                    items.addAll(result.items());
                    rejected += result.rejected();
                } catch (Exception ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    failedFeeds++;
                    log.warn("news_feed_failed category={} source={}", category, feed.sourceName(), ex);
                }
            }
            if (!items.isEmpty()) {
                Map<String, NewsItem> byId = new LinkedHashMap<>(); // intra-batch dedupe, last wins
                items.forEach(item -> byId.put(item.getId(), item));
                repository.saveAll(byId.values()); // save() upserts by _id
                accepted += byId.size();
                prune(category);
            }
        }
        log.info("news_refresh accepted={} rejected={} failedFeeds={}", accepted, rejected, failedFeeds);
    }

    private void prune(NewsCategory category) {
        List<NewsItem> all = new ArrayList<>(repository.findByCategory(category));
        if (all.size() <= properties.keepPerCategory()) {
            return;
        }
        all.sort(Comparator.comparing(NewsItem::getPublishedAt).reversed());
        List<String> excess = all.subList(properties.keepPerCategory(), all.size())
                .stream().map(NewsItem::getId).toList();
        repository.deleteAllById(excess);
    }
}
