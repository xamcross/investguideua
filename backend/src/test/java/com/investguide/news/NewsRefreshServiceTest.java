package com.investguide.news;

import com.investguide.config.NewsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature 016 FR-3: stale-vs-fresh gating, single-flight refresh, per-feed isolation, prune-to-N,
 * and the never-delete-on-failure invariant.
 */
class NewsRefreshServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    /** Runs refresh jobs synchronously so assertions see their effects immediately. */
    private static final Executor DIRECT = Runnable::run;

    private static final String VALID_RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>t</title>
            <item><title>Headline one</title><link>https://f.example.com/1</link>
            <pubDate>Sat, 25 Jul 2026 08:30:00 GMT</pubDate></item>
            </channel></rss>""";

    private final NewsItemRepository repository = mock(NewsItemRepository.class);
    private final NewsFeedFetcher fetcher = mock(NewsFeedFetcher.class);

    private NewsProperties properties(int keepPerCategory) {
        return new NewsProperties(Duration.ofMinutes(30), Duration.ofSeconds(5), keepPerCategory, 8,
                List.of(new NewsProperties.Feed("UA-1", "https://ua1.example.com/rss"),
                        new NewsProperties.Feed("UA-2", "https://ua2.example.com/rss")),
                List.of(new NewsProperties.Feed("W-1", "https://w1.example.com/rss")));
    }

    private NewsRefreshService service(int keepPerCategory) {
        return new NewsRefreshService(repository, fetcher, properties(keepPerCategory), DIRECT, CLOCK);
    }

    private static NewsItem stored(String link, Instant publishedAt, Instant fetchedAt) {
        return new NewsItem(NewsItem.idFor(link), NewsCategory.UKRAINE, "s", "t", link,
                publishedAt, fetchedAt);
    }

    @BeforeEach
    void feedsSucceedByDefault() throws Exception {
        when(fetcher.fetch(any())).thenReturn(VALID_RSS.getBytes(UTF_8));
    }

    @Test
    void freshDataTriggersNoRefresh() throws Exception {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(
                Optional.of(stored("https://x/1", NOW, NOW.minus(Duration.ofMinutes(5)))));

        service(20).maybeRefreshAsync();

        verify(fetcher, never()).fetch(any());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void staleDataRefreshesAllConfiguredFeeds() throws Exception {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(
                Optional.of(stored("https://x/1", NOW, NOW.minus(Duration.ofMinutes(31)))));
        when(repository.findByCategory(any())).thenReturn(List.of());

        service(20).maybeRefreshAsync();

        verify(fetcher).fetch("https://ua1.example.com/rss");
        verify(fetcher).fetch("https://ua2.example.com/rss");
        verify(fetcher).fetch("https://w1.example.com/rss");
        verify(repository, times(2)).saveAll(any()); // one batch per category
    }

    @Test
    void emptyCollectionCountsAsStale() throws Exception {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.empty());
        when(repository.findByCategory(any())).thenReturn(List.of());

        service(20).maybeRefreshAsync();

        verify(fetcher, atLeastOnce()).fetch(any());
    }

    @Test
    void oneFailingFeedDoesNotBlockOthersAndNeverDeletes() throws Exception {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.empty());
        when(repository.findByCategory(any())).thenReturn(List.of());
        when(fetcher.fetch("https://ua1.example.com/rss")).thenThrow(new RuntimeException("down"));

        service(20).maybeRefreshAsync();

        // UA-2 and W-1 still fetched and saved; nothing deleted because of the failure.
        verify(fetcher).fetch("https://ua2.example.com/rss");
        verify(fetcher).fetch("https://w1.example.com/rss");
        verify(repository, times(2)).saveAll(any());
        verify(repository, never()).deleteAll();
        verify(repository, never()).deleteAllById(any());
    }

    @Test
    void allFeedsFailingSavesAndDeletesNothing() throws Exception {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.empty());
        when(fetcher.fetch(any())).thenThrow(new RuntimeException("all down"));

        service(20).maybeRefreshAsync();

        verify(repository, never()).saveAll(any());
        verify(repository, never()).deleteAll();
        verify(repository, never()).deleteAllById(any());
    }

    @Test
    void prunesEachCategoryToKeepLimitByPublishedAtDesc() throws Exception {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.empty());
        // keep-per-category = 1: the older of two stored UKRAINE items must be deleted.
        NewsItem newer = stored("https://x/new", NOW, NOW);
        NewsItem older = stored("https://x/old", NOW.minus(Duration.ofDays(2)), NOW);
        when(repository.findByCategory(NewsCategory.UKRAINE)).thenReturn(List.of(older, newer));
        when(repository.findByCategory(NewsCategory.WORLD)).thenReturn(List.of());

        service(1).maybeRefreshAsync();

        verify(repository).deleteAllById(List.of(NewsItem.idFor("https://x/old")));
    }

    @Test
    void concurrentStaleReadsCauseExactlyOneRefresh() throws Exception {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.empty());
        when(repository.findByCategory(any())).thenReturn(List.of());
        Clock movableClock = mock(Clock.class);
        when(movableClock.instant()).thenReturn(NOW);
        // Executor that defers execution: both calls happen while the first job is still "queued".
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        NewsRefreshService deferred = new NewsRefreshService(
                repository, fetcher, properties(20), queued::add, movableClock);

        deferred.maybeRefreshAsync();
        deferred.maybeRefreshAsync(); // in-flight guard + attempt backoff must reject this one

        assertThat(queued).hasSize(1);
        queued.get(0).run();
        // After completion AND TTL expiry, a later stale read refreshes again.
        when(movableClock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(31)));
        deferred.maybeRefreshAsync();
        assertThat(queued).hasSize(2);
    }

    @Test
    void failedRefreshBacksOffForTtlBeforeRetrying() throws Exception {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.empty());
        when(fetcher.fetch(any())).thenThrow(new RuntimeException("all down"));
        Clock movableClock = mock(Clock.class);
        when(movableClock.instant()).thenReturn(NOW);
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        NewsRefreshService service = new NewsRefreshService(
                repository, fetcher, properties(20), queued::add, movableClock);

        service.maybeRefreshAsync();
        queued.get(0).run(); // refresh runs, every feed fails, nothing is stored

        service.maybeRefreshAsync(); // still within TTL of the failed attempt -> no hammering
        assertThat(queued).hasSize(1);

        when(movableClock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(31)));
        service.maybeRefreshAsync(); // TTL elapsed -> retry allowed
        assertThat(queued).hasSize(2);
    }

    @Test
    void intraBatchDuplicateLinksAreDedupedLastWins() throws Exception {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.empty());
        when(repository.findByCategory(any())).thenReturn(List.of());
        String rssA = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>t</title>
                <item><title>First headline</title><link>https://dup.example.com/1</link>
                <pubDate>Sat, 25 Jul 2026 08:30:00 GMT</pubDate></item>
                </channel></rss>""";
        String rssB = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>t</title>
                <item><title>Second headline</title><link>https://dup.example.com/1</link>
                <pubDate>Sat, 25 Jul 2026 09:00:00 GMT</pubDate></item>
                </channel></rss>""";
        when(fetcher.fetch("https://ua1.example.com/rss")).thenReturn(rssA.getBytes(UTF_8));
        when(fetcher.fetch("https://ua2.example.com/rss")).thenReturn(rssB.getBytes(UTF_8));

        service(20).maybeRefreshAsync();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<NewsItem>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository, times(2)).saveAll(captor.capture());
        // UKRAINE is processed first, so the first captured batch is the UKRAINE pass.
        List<NewsItem> ukraineBatch = new ArrayList<>();
        captor.getAllValues().get(0).forEach(ukraineBatch::add);
        assertThat(ukraineBatch).hasSize(1);
        assertThat(ukraineBatch.get(0).getTitle()).isEqualTo("Second headline");
    }

    @Test
    void rejectedSubmissionResetsGuardSoLaterRefreshCanRun() throws Exception {
        when(repository.findFirstByOrderByFetchedAtDesc()).thenReturn(Optional.empty());
        when(repository.findByCategory(any())).thenReturn(List.of());
        Clock movableClock = mock(Clock.class);
        when(movableClock.instant()).thenReturn(NOW);
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);
        Executor flaky = task -> {
            if (first.compareAndSet(true, false)) {
                throw new java.util.concurrent.RejectedExecutionException("shut down");
            }
            queued.add(task);
        };
        NewsRefreshService service = new NewsRefreshService(
                repository, fetcher, properties(20), flaky, movableClock);

        service.maybeRefreshAsync(); // submission rejected; guard must reset, no crash escapes

        when(movableClock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(31)));
        service.maybeRefreshAsync(); // past TTL backoff -> schedules normally
        assertThat(queued).hasSize(1);
    }
}
