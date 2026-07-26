# Financial News Widget (016) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Headlines-only financial news widget (Ukrainian + world RSS) on the landing and search pages, served from a Mongo cache with stale-while-revalidate refresh.

**Architecture:** Public `GET /api/v1/news/latest` always reads the `newsItems` Mongo collection; when the newest stored `fetchedAt` is older than 30 min it triggers exactly one async refresh that fetches configured RSS feeds (JDK `HttpClient` + Rome parser), normalizes, upserts by link-hash, and prunes to 20/category. A standalone Angular `NewsWidgetComponent` renders two lists and hides itself entirely on error/empty.

**Tech Stack:** Java 21 / Spring Boot 3.x / MongoDB / Rome (`com.rometools:rome:2.1.0`); Angular 17+ standalone / ngx-translate / Karma+Jasmine.

## Global Constraints

- Branch: all work on `016-financial-news-widget` (already created; spec committed).
- Spec: `specs/016-financial-news-widget/spec.md` — FR numbers below refer to it.
- Backend module layout: new package `com.investguide.news` (mirrors `bonds`/`metals`).
- API path prefix `/api/v1` via `@RequestMapping("/api/v1")` (repo convention, see `BondPriceController`).
- HTTP client: JDK `java.net.http.HttpClient` only — no third-party HTTP SDK (repo convention).
- No article bodies stored or displayed — headline + source + link only (licensing).
- A failed or empty fetch never deletes stored items.
- Frontend: standalone components, `ChangeDetectionStrategy.OnPush`, inline templates, `ig-` CSS prefix, i18n keys in `frontend/public/i18n/{uk,en}.json` (never hardcoded user-facing strings).
- No `.ps1`/`.cmd`/`.bat` files are touched by this feature; if any are added anyway they must be pure ASCII (CLAUDE.md).
- Tests: backend plain JUnit + Mockito (`mvn -q test` from `backend/`); security-chain slice tests via `@WebMvcTest` + `@Import(SecurityConfig.class, ...)`; frontend Karma (`npm run test:ci` from `frontend/`).
- Every task ends with a commit on the feature branch.
- CLAUDE.md verification: completion requires the mandatory role sub-agent review (Task 9), run without asking permission.

---

### Task 1: Backend domain — `NewsCategory`, `NewsItem`, repository

**Files:**
- Create: `backend/src/main/java/com/investguide/news/NewsCategory.java`
- Create: `backend/src/main/java/com/investguide/news/NewsItem.java`
- Create: `backend/src/main/java/com/investguide/news/NewsItemRepository.java`
- Test: `backend/src/test/java/com/investguide/news/NewsItemTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum NewsCategory { UKRAINE, WORLD }`; `NewsItem(String id, NewsCategory category, String sourceName, String title, String link, Instant publishedAt, Instant fetchedAt)` with getters `getId/getCategory/getSourceName/getTitle/getLink/getPublishedAt/getFetchedAt` and `static String idFor(String link)` (SHA-256 hex); `NewsItemRepository extends MongoRepository<NewsItem, String>` with `List<NewsItem> findByCategoryOrderByPublishedAtDesc(NewsCategory category, Pageable pageable)`, `Optional<NewsItem> findFirstByOrderByFetchedAtDesc()`, `List<NewsItem> findByCategory(NewsCategory category)`.

- [ ] **Step 1: Write the failing test**

```java
package com.investguide.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Feature 016 FR-1: the link hash is the stable dedupe/upsert key. */
class NewsItemTest {

    @Test
    void idForIsDeterministicSha256Hex() {
        String a = NewsItem.idFor("https://example.com/article-1");
        String b = NewsItem.idFor("https://example.com/article-1");
        String c = NewsItem.idFor("https://example.com/article-2");

        assertThat(a).isEqualTo(b).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(a).isNotEqualTo(c);
        // Pinned value: catches accidental algorithm/encoding changes (would orphan stored rows).
        assertThat(NewsItem.idFor("x")).isEqualTo(
                "2d711642b726b04401627ca9fbac32f5c8530fb1903cc4db02258717921a4881");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `backend/`): `mvn -q test -Dtest=NewsItemTest`
Expected: COMPILATION ERROR — `NewsItem` does not exist.

- [ ] **Step 3: Write minimal implementation**

`NewsCategory.java`:

```java
package com.investguide.news;

/** News feed category (feature 016): Ukrainian financial news vs world markets. */
public enum NewsCategory { UKRAINE, WORLD }
```

`NewsItem.java`:

```java
package com.investguide.news;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * One cached RSS headline (feature 016, FR-1). {@code _id} is the SHA-256 hex of the item link, so
 * repeated fetches upsert instead of duplicating. Headline + attribution + link only — no article
 * body is ever stored (licensing).
 */
@Document("newsItems")
public class NewsItem {

    @Id
    private String id;
    private NewsCategory category;
    private String sourceName;
    private String title;
    private String link;
    private Instant publishedAt;
    private Instant fetchedAt;

    @SuppressWarnings("unused") // Spring Data
    private NewsItem() {
    }

    public NewsItem(String id, NewsCategory category, String sourceName, String title,
                    String link, Instant publishedAt, Instant fetchedAt) {
        this.id = id;
        this.category = category;
        this.sourceName = sourceName;
        this.title = title;
        this.link = link;
        this.publishedAt = publishedAt;
        this.fetchedAt = fetchedAt;
    }

    /** Stable dedupe key: SHA-256 hex of the item link. */
    public static String idFor(String link) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(link.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex); // mandated by the JCA spec
        }
    }

    public String getId() { return id; }
    public NewsCategory getCategory() { return category; }
    public String getSourceName() { return sourceName; }
    public String getTitle() { return title; }
    public String getLink() { return link; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getFetchedAt() { return fetchedAt; }
}
```

`NewsItemRepository.java`:

```java
package com.investguide.news;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NewsItemRepository extends MongoRepository<NewsItem, String> {

    List<NewsItem> findByCategoryOrderByPublishedAtDesc(NewsCategory category, Pageable pageable);

    /** Newest fetch stamp across both categories — the staleness probe (FR-3). */
    Optional<NewsItem> findFirstByOrderByFetchedAtDesc();

    List<NewsItem> findByCategory(NewsCategory category);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=NewsItemTest`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/investguide/news backend/src/test/java/com/investguide/news
git commit -m "feat(news): NewsItem domain, category enum, repository (016 FR-1)"
```

---

### Task 2: Backend RSS parsing + normalization — `NewsFeedParser`

**Files:**
- Modify: `backend/pom.xml` (add Rome dependency in `<dependencies>`)
- Create: `backend/src/main/java/com/investguide/news/NewsFeedException.java`
- Create: `backend/src/main/java/com/investguide/news/NewsFeedParser.java`
- Create: `backend/src/test/resources/news/rss-valid.xml`
- Create: `backend/src/test/resources/news/rss-dirty.xml`
- Create: `backend/src/test/resources/news/rss-malformed.xml`
- Test: `backend/src/test/java/com/investguide/news/NewsFeedParserTest.java`

**Interfaces:**
- Consumes: `NewsItem`, `NewsCategory`, `NewsItem.idFor(String)` from Task 1.
- Produces: `NewsFeedParser.Result(List<NewsItem> items, int rejected)`; `static Result NewsFeedParser.parse(byte[] xml, NewsCategory category, String sourceName, Instant fetchedAt)` — throws `NewsFeedException` (unchecked) on unparseable XML; skipped items are counted in `rejected`.

- [ ] **Step 1: Add the Rome dependency**

In `backend/pom.xml`, inside `<dependencies>`, after the `jjwt-jackson` dependency block:

```xml
        <!-- RSS/Atom parsing for the financial news widget (feature 016). -->
        <dependency>
            <groupId>com.rometools</groupId>
            <artifactId>rome</artifactId>
            <version>2.1.0</version>
        </dependency>
```

Run: `mvn -q -DskipTests compile` — Expected: BUILD SUCCESS (dependency resolves).

- [ ] **Step 2: Create the test fixtures**

`backend/src/test/resources/news/rss-valid.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Sample Business Feed</title>
    <link>https://feed.example.com/</link>
    <description>Test fixture: fully valid items</description>
    <item>
      <title>NBU keeps key rate at 15.5 percent</title>
      <link>https://feed.example.com/nbu-rate</link>
      <pubDate>Sat, 25 Jul 2026 08:30:00 GMT</pubDate>
    </item>
    <item>
      <title>Government bonds auction raises UAH 12 bln</title>
      <link>https://feed.example.com/bond-auction</link>
      <pubDate>Sat, 25 Jul 2026 07:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>
```

`backend/src/test/resources/news/rss-dirty.xml` (HTML-laden title, missing link, missing pubDate, relative link — only the first item is acceptable):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Dirty Feed</title>
    <link>https://dirty.example.com/</link>
    <description>Test fixture: normalization and rejects</description>
    <item>
      <title><![CDATA[ <b>Markets&nbsp;rally</b> as &amp; oil   drops ]]></title>
      <link>https://dirty.example.com/markets-rally</link>
      <pubDate>Fri, 24 Jul 2026 15:45:00 GMT</pubDate>
    </item>
    <item>
      <title>No link item</title>
      <pubDate>Fri, 24 Jul 2026 14:00:00 GMT</pubDate>
    </item>
    <item>
      <title>No date item</title>
      <link>https://dirty.example.com/no-date</link>
    </item>
    <item>
      <title>Relative link item</title>
      <link>/relative/path</link>
      <pubDate>Fri, 24 Jul 2026 13:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>
```

`backend/src/test/resources/news/rss-malformed.xml` (truncated on purpose — not well-formed XML):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Broken feed</title>
    <item>
      <title>Never closed
```

- [ ] **Step 3: Write the failing test**

```java
package com.investguide.news;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature 016 FR-3 normalization: strip HTML/CDATA, collapse whitespace, cap at 200 chars, require
 * non-blank title + absolute http(s) link + parseable date; anything else is rejected (counted),
 * never thrown. Unparseable XML throws {@link NewsFeedException} for the caller's per-feed isolation.
 */
class NewsFeedParserTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-07-26T10:00:00Z");

    private static byte[] fixture(String name) {
        try (InputStream in = NewsFeedParserTest.class.getResourceAsStream("/news/" + name)) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Test
    void parsesValidFeedIntoNewsItems() {
        NewsFeedParser.Result result = NewsFeedParser.parse(
                fixture("rss-valid.xml"), NewsCategory.UKRAINE, "Тест", FETCHED_AT);

        assertThat(result.rejected()).isZero();
        assertThat(result.items()).hasSize(2);
        NewsItem first = result.items().get(0);
        assertThat(first.getId()).isEqualTo(NewsItem.idFor("https://feed.example.com/nbu-rate"));
        assertThat(first.getCategory()).isEqualTo(NewsCategory.UKRAINE);
        assertThat(first.getSourceName()).isEqualTo("Тест");
        assertThat(first.getTitle()).isEqualTo("NBU keeps key rate at 15.5 percent");
        assertThat(first.getLink()).isEqualTo("https://feed.example.com/nbu-rate");
        assertThat(first.getPublishedAt()).isEqualTo(Instant.parse("2026-07-25T08:30:00Z"));
        assertThat(first.getFetchedAt()).isEqualTo(FETCHED_AT);
    }

    @Test
    void normalizesDirtyTitlesAndRejectsIncompleteItems() {
        NewsFeedParser.Result result = NewsFeedParser.parse(
                fixture("rss-dirty.xml"), NewsCategory.WORLD, "Dirty", FETCHED_AT);

        // 1 acceptable item; missing-link, missing-date and relative-link items are rejected.
        assertThat(result.items()).hasSize(1);
        assertThat(result.rejected()).isEqualTo(3);
        // Tags stripped, entities unescaped, whitespace collapsed.
        assertThat(result.items().get(0).getTitle()).isEqualTo("Markets rally as & oil drops");
    }

    @Test
    void capsTitleAt200Chars() {
        String longTitle = "word ".repeat(60); // 300 chars
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>t</title>
                <item><title>%s</title><link>https://x.example.com/long</link>
                <pubDate>Sat, 25 Jul 2026 08:30:00 GMT</pubDate></item>
                </channel></rss>""".formatted(longTitle);

        NewsFeedParser.Result result = NewsFeedParser.parse(
                xml.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                NewsCategory.WORLD, "Long", FETCHED_AT);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getTitle().length()).isLessThanOrEqualTo(200);
    }

    @Test
    void malformedXmlThrowsNewsFeedException() {
        assertThatThrownBy(() -> NewsFeedParser.parse(
                fixture("rss-malformed.xml"), NewsCategory.WORLD, "Broken", FETCHED_AT))
                .isInstanceOf(NewsFeedException.class);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -q test -Dtest=NewsFeedParserTest`
Expected: COMPILATION ERROR — `NewsFeedParser` does not exist.

- [ ] **Step 5: Write the implementation**

`NewsFeedException.java`:

```java
package com.investguide.news;

/** A feed could not be fetched or parsed (feature 016). Caught per feed — never fatal. */
public class NewsFeedException extends RuntimeException {
    public NewsFeedException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`NewsFeedParser.java`:

```java
package com.investguide.news;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Pure RSS/Atom -> {@link NewsItem} normalization (feature 016, FR-3). An item needs a non-blank
 * title, an absolute http(s) link and a parseable published (or updated) date; everything else is
 * skipped and counted in {@link Result#rejected()}. Titles are stripped of HTML/CDATA, entity-
 * unescaped, whitespace-collapsed and capped at {@value #MAX_TITLE_LENGTH} chars.
 */
public final class NewsFeedParser {

    static final int MAX_TITLE_LENGTH = 200;

    private NewsFeedParser() {
    }

    public record Result(List<NewsItem> items, int rejected) {
    }

    public static Result parse(byte[] xml, NewsCategory category, String sourceName, Instant fetchedAt) {
        SyndFeed feed;
        try {
            feed = new SyndFeedInput().build(new XmlReader(new ByteArrayInputStream(xml)));
        } catch (Exception ex) {
            throw new NewsFeedException("Unparseable feed from " + sourceName, ex);
        }

        List<NewsItem> items = new ArrayList<>();
        int rejected = 0;
        for (SyndEntry entry : feed.getEntries()) {
            String title = cleanTitle(entry.getTitle());
            String link = entry.getLink() == null ? "" : entry.getLink().trim();
            Date published = entry.getPublishedDate() != null
                    ? entry.getPublishedDate() : entry.getUpdatedDate();
            if (title.isBlank() || !isAbsoluteHttpUrl(link) || published == null) {
                rejected++;
                continue;
            }
            items.add(new NewsItem(NewsItem.idFor(link), category, sourceName, title, link,
                    published.toInstant(), fetchedAt));
        }
        return new Result(items, rejected);
    }

    private static String cleanTitle(String raw) {
        if (raw == null) {
            return "";
        }
        // &nbsp; unescapes to U+00A0, which Java's \s does NOT match - the class collapses it too.
        String text = HtmlUtils.htmlUnescape(raw.replaceAll("<[^>]*>", " "))
                .replaceAll("[\\s\\u00A0]+", " ")
                .trim();
        return text.length() <= MAX_TITLE_LENGTH ? text : text.substring(0, MAX_TITLE_LENGTH).trim();
    }

    private static boolean isAbsoluteHttpUrl(String link) {
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            return false;
        }
        try {
            return URI.create(link).getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
```

Note: the collapse regex includes ` ` because `&nbsp;` unescapes to a non-breaking space,
which plain `\s` does not match — that is what makes the dirty-fixture assertion pass.

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q test -Dtest=NewsFeedParserTest`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/investguide/news backend/src/test
git commit -m "feat(news): Rome-based RSS parser with normalization and reject counting (016 FR-3)"
```

---

### Task 3: Backend config + HTTP fetcher — `NewsProperties`, yml defaults, `HttpNewsFeedFetcher`, executor

**Files:**
- Create: `backend/src/main/java/com/investguide/config/NewsProperties.java`
- Modify: `backend/src/main/java/com/investguide/InvestGuideApplication.java` (register properties)
- Modify: `backend/src/main/resources/application.yml` (append `news:` section after `metals:`)
- Create: `backend/src/main/java/com/investguide/news/NewsFeedFetcher.java`
- Create: `backend/src/main/java/com/investguide/news/HttpNewsFeedFetcher.java`
- Create: `backend/src/main/java/com/investguide/news/NewsConfig.java`

**Interfaces:**
- Consumes: `NewsCategory` from Task 1.
- Produces: `NewsProperties(Duration refreshTtl, Duration fetchTimeout, int keepPerCategory, int servePerCategory, List<Feed> ukraine, List<Feed> world)` with `record Feed(String sourceName, String url)` and `List<Feed> feedsFor(NewsCategory category)`; `interface NewsFeedFetcher { byte[] fetch(String url) throws Exception; }`; Spring bean `ExecutorService newsRefreshExecutor`.

- [ ] **Step 1: Verify the default feeds are live (network step, run locally)**

For each candidate URL run `curl -fsSL -A "InvestGuideUA/1.0 (+https://investguideua.com)" --max-time 10 "<url>" | head -c 300` and keep it only if the output starts with `<?xml` or `<rss` or `<feed`:

- UKRAINE candidates: `https://www.epravda.com.ua/rss/`; `https://minfin.com.ua/rss/`
- WORLD candidates: `https://www.cnbc.com/id/100003114/device/rss/rss.html`; `https://feeds.content.dowjones.io/public/rss/mw_topstories`; fallback `https://feeds.a.dj.com/rss/RSSMarketsMain.xml`

Requirement: at least ONE live feed per category. Record the surviving list — it becomes the yml default in Step 3. If a candidate is dead, drop it; do not ship an unverified URL.

- [ ] **Step 2: Create `NewsProperties` and register it**

`NewsProperties.java` (in `com.investguide.config`, mirroring `MetalsProperties`):

```java
package com.investguide.config;

import com.investguide.news.NewsCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Financial news widget [CONFIG] (feature 016). Feed lists are config, not code, so a dead source
 * is a properties swap. All values are non-secret tunables; defaults live in application.yml.
 */
@Validated
@ConfigurationProperties(prefix = "news")
public record NewsProperties(@NotNull Duration refreshTtl,
                             @NotNull Duration fetchTimeout,
                             @Positive int keepPerCategory,
                             @Positive int servePerCategory,
                             @NotNull @Valid List<Feed> ukraine,
                             @NotNull @Valid List<Feed> world) {

    public record Feed(@NotBlank String sourceName, @NotBlank String url) {
    }

    public List<Feed> feedsFor(NewsCategory category) {
        return category == NewsCategory.UKRAINE ? ukraine : world;
    }
}
```

In `InvestGuideApplication.java` add `NewsProperties.class` to the `@EnableConfigurationProperties({...})` list (alphabetical slot: after `MetalsProperties.class`, before `PaymentProperties.class`) and add the import `com.investguide.config.NewsProperties`.

- [ ] **Step 3: Append the `news:` section to `application.yml`**

After the `metals:` block (currently ending at `primary-rate-group:` around line 87), using ONLY the feeds that survived Step 1 (the list below assumes all four passed):

```yaml
news:
  # Financial news widget (feature 016). Non-secret tunables; feeds are swappable without code.
  refresh-ttl: 30m
  fetch-timeout: 5s
  keep-per-category: 20
  serve-per-category: 8
  ukraine:
    - source-name: "Економічна правда"
      url: "https://www.epravda.com.ua/rss/"
    - source-name: "Мінфін"
      url: "https://minfin.com.ua/rss/"
  world:
    - source-name: "CNBC"
      url: "https://www.cnbc.com/id/100003114/device/rss/rss.html"
    - source-name: "MarketWatch"
      url: "https://feeds.content.dowjones.io/public/rss/mw_topstories"
```

- [ ] **Step 4: Create the fetcher and the executor config**

`NewsFeedFetcher.java`:

```java
package com.investguide.news;

/** Fetches raw feed bytes (feature 016). Interface exists as the test seam for the refresh service. */
public interface NewsFeedFetcher {
    byte[] fetch(String url) throws Exception;
}
```

`HttpNewsFeedFetcher.java`:

```java
package com.investguide.news;

import com.investguide.config.NewsProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * JDK {@link HttpClient} feed fetch (feature 016) — repo convention, no third-party HTTP SDK.
 * Explicit User-Agent: several publishers reject the default Java UA. Feed URLs come exclusively
 * from configuration (no user input -> no SSRF surface). Responses are streamed and capped at
 * {@value #MAX_FEED_BYTES} bytes — a misbehaving feed must not balloon the heap.
 */
@Component
public class HttpNewsFeedFetcher implements NewsFeedFetcher {

    static final int MAX_FEED_BYTES = 5 * 1024 * 1024;

    private final HttpClient client;
    private final Duration timeout;

    public HttpNewsFeedFetcher(NewsProperties properties) {
        this.timeout = properties.fetchTimeout();
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public byte[] fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "InvestGuideUA/1.0 (+https://investguideua.com)")
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Feed HTTP " + response.statusCode() + " from " + url);
            }
            byte[] body = in.readNBytes(MAX_FEED_BYTES + 1);
            if (body.length > MAX_FEED_BYTES) {
                throw new IOException("Feed body over " + MAX_FEED_BYTES + " bytes from " + url);
            }
            return body;
        }
    }
}
```

`NewsConfig.java`:

```java
package com.investguide.news;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Single-thread daemon executor for async news refresh (feature 016, FR-3). */
@Configuration
public class NewsConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService newsRefreshExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "news-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }
}
```

- [ ] **Step 5: Verify the context still boots (config binding)**

Run: `mvn -q test`
Expected: BUILD SUCCESS — all existing tests green (`HttpNewsFeedFetcher` binding `NewsProperties`
compiles; no context-loading test exists, so binding is exercised for real in Task 5's slice test
and at first boot). No new tests in this task: the fetcher is a thin I/O boundary verified by the
Step 1 curl checks and mocked everywhere else — state this in the PR if asked.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/investguide/config/NewsProperties.java \
        backend/src/main/java/com/investguide/InvestGuideApplication.java \
        backend/src/main/resources/application.yml \
        backend/src/main/java/com/investguide/news
git commit -m "feat(news): config-driven feed list, JDK HttpClient fetcher, refresh executor (016 FR-2)"
```

---

### Task 4: Backend `NewsRefreshService` — stale-while-revalidate core

**Files:**
- Create: `backend/src/main/java/com/investguide/news/NewsRefreshService.java`
- Test: `backend/src/test/java/com/investguide/news/NewsRefreshServiceTest.java`

**Interfaces:**
- Consumes: `NewsItemRepository`, `NewsFeedParser.parse(...)`, `NewsFeedFetcher`, `NewsProperties`, bean `newsRefreshExecutor` (Tasks 1–3).
- Produces: `public void maybeRefreshAsync()` — the only method the controller calls (Task 5). Package-private test-seam constructor `(NewsItemRepository, NewsFeedFetcher, NewsProperties, Executor, Clock)` mirroring `BondPriceService`'s Clock seam.

- [ ] **Step 1: Write the failing tests**

```java
package com.investguide.news;

import com.investguide.config.NewsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=NewsRefreshServiceTest`
Expected: COMPILATION ERROR — `NewsRefreshService` does not exist.

- [ ] **Step 3: Write the implementation**

```java
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
        executor.execute(() -> {
            try {
                refresh();
            } catch (RuntimeException ex) {
                log.warn("news_refresh crashed: {}", ex.toString());
            } finally {
                refreshInFlight.set(false);
            }
        });
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
                    failedFeeds++;
                    log.warn("news_feed_failed category={} source={} error={}",
                            category, feed.sourceName(), ex.toString());
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=NewsRefreshServiceTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/investguide/news/NewsRefreshService.java \
        backend/src/test/java/com/investguide/news/NewsRefreshServiceTest.java
git commit -m "feat(news): stale-while-revalidate refresh service with single-flight guard (016 FR-3)"
```

---

### Task 5: Backend `NewsController` + DTOs + `SecurityConfig` public route

**Files:**
- Create: `backend/src/main/java/com/investguide/news/dto/NewsItemResponse.java`
- Create: `backend/src/main/java/com/investguide/news/dto/NewsLatestResponse.java`
- Create: `backend/src/main/java/com/investguide/news/NewsController.java`
- Modify: `backend/src/main/java/com/investguide/common/security/SecurityConfig.java` (PUBLIC_GET array, lines 38–44)
- Test: `backend/src/test/java/com/investguide/news/NewsControllerTest.java`

**Interfaces:**
- Consumes: `NewsItemRepository.findByCategoryOrderByPublishedAtDesc(category, pageable)`, `NewsRefreshService.maybeRefreshAsync()`, `NewsProperties.servePerCategory()`.
- Produces: `GET /api/v1/news/latest` → `200 {"ukraine":[{sourceName,title,link,publishedAt}],"world":[...]}` with `Cache-Control: max-age=300, public` — the contract Task 6's frontend service consumes.

- [ ] **Step 1: Write the failing test** (mirrors `BondPriceControllerTest`: real `SecurityConfig` over a controller slice)

```java
package com.investguide.news;

import com.investguide.common.security.JwtService;
import com.investguide.common.security.RestAuthEntryPoints;
import com.investguide.common.security.SecurityConfig;
import com.investguide.config.NewsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 016 FR-4: {@code GET /api/v1/news/latest} is anonymous-public, serves stored items only
 * (never a synchronous fetch), pins the exact cache header, and 200s with empty lists when the
 * collection is empty. Exercises the REAL {@link SecurityConfig} chain (mirrors
 * {@code BondPriceControllerTest}).
 */
@WebMvcTest(controllers = NewsController.class, properties = {
        "llm.api-key=test-key",
        "security.jwt-secret=test-secret-test-secret-test-secret-123456"
})
@Import({SecurityConfig.class, RestAuthEntryPoints.class, JwtService.class})
class NewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsItemRepository repository;

    @MockitoBean
    private NewsRefreshService refreshService;

    @MockitoBean
    private NewsProperties properties; // record: Mockito 5 inline mock-maker handles final types

    private static NewsItem item(String link, NewsCategory category) {
        return new NewsItem(NewsItem.idFor(link), category, "Src", "Title " + link, link,
                Instant.parse("2026-07-26T09:00:00Z"), Instant.parse("2026-07-26T10:00:00Z"));
    }

    @Test
    void anonymousGetReturnsBothListsWithExactCacheHeader() throws Exception {
        when(properties.servePerCategory()).thenReturn(8);
        when(repository.findByCategoryOrderByPublishedAtDesc(NewsCategory.UKRAINE, PageRequest.of(0, 8)))
                .thenReturn(List.of(item("https://ua.example.com/1", NewsCategory.UKRAINE)));
        when(repository.findByCategoryOrderByPublishedAtDesc(NewsCategory.WORLD, PageRequest.of(0, 8)))
                .thenReturn(List.of(item("https://w.example.com/1", NewsCategory.WORLD)));

        mockMvc.perform(get("/api/v1/news/latest"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$.ukraine[0].sourceName").value("Src"))
                .andExpect(jsonPath("$.ukraine[0].title").value("Title https://ua.example.com/1"))
                .andExpect(jsonPath("$.ukraine[0].link").value("https://ua.example.com/1"))
                .andExpect(jsonPath("$.ukraine[0].publishedAt").value("2026-07-26T09:00:00Z"))
                .andExpect(jsonPath("$.ukraine[0].id").doesNotExist())
                .andExpect(jsonPath("$.world[0].link").value("https://w.example.com/1"));

        verify(refreshService).maybeRefreshAsync();
    }

    @Test
    void emptyCollectionReturns200WithEmptyLists() throws Exception {
        when(properties.servePerCategory()).thenReturn(8);
        when(repository.findByCategoryOrderByPublishedAtDesc(NewsCategory.UKRAINE, PageRequest.of(0, 8)))
                .thenReturn(List.of());
        when(repository.findByCategoryOrderByPublishedAtDesc(NewsCategory.WORLD, PageRequest.of(0, 8)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/news/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ukraine").isEmpty())
                .andExpect(jsonPath("$.world").isEmpty());

        verify(refreshService).maybeRefreshAsync(); // empty store must still seed a refresh
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=NewsControllerTest`
Expected: COMPILATION ERROR — `NewsController` does not exist.

- [ ] **Step 3: Write the implementation**

`dto/NewsItemResponse.java`:

```java
package com.investguide.news.dto;

import com.investguide.news.NewsItem;

import java.time.Instant;

/** Public headline row (feature 016, FR-4). No id/category/fetchedAt — presentation needs only these. */
public record NewsItemResponse(String sourceName, String title, String link, Instant publishedAt) {

    public static NewsItemResponse from(NewsItem item) {
        return new NewsItemResponse(item.getSourceName(), item.getTitle(), item.getLink(),
                item.getPublishedAt());
    }
}
```

`dto/NewsLatestResponse.java`:

```java
package com.investguide.news.dto;

import java.util.List;

/** Response body of GET /api/v1/news/latest (feature 016, FR-4). */
public record NewsLatestResponse(List<NewsItemResponse> ukraine, List<NewsItemResponse> world) {
}
```

`NewsController.java`:

```java
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
 * Public financial-news read (feature 016, FR-4). Serves ONLY stored data — the response time is a
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
```

In `SecurityConfig.java`, extend `PUBLIC_GET` (keep the existing comment style):

```java
    private static final String[] PUBLIC_GET = {
            "/api/v1/ping",
            // Latest bond-price snapshot (015): anonymous read for the public SEO data pages. The
            // exact-match ADMIN rule on /api/v1/bond-prices below does not cover this sub-path.
            "/api/v1/bond-prices/latest",
            // Latest news headlines (016): anonymous read for the landing/search news widget.
            "/api/v1/news/latest",
            "/actuator/health"
    };
```

- [ ] **Step 4: Run test to verify it passes, then the full suite**

Run: `mvn -q test -Dtest=NewsControllerTest` — Expected: PASS (2 tests).
Run: `mvn -q test` — Expected: BUILD SUCCESS, zero failures (whole backend green).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/investguide/news backend/src/test/java/com/investguide/news \
        backend/src/main/java/com/investguide/common/security/SecurityConfig.java
git commit -m "feat(news): public GET /api/v1/news/latest with pinned cache header (016 FR-4)"
```

---

### Task 6: Frontend models, `NewsService`, `timeAgo` util

**Files:**
- Create: `frontend/src/app/features/news/news.models.ts`
- Create: `frontend/src/app/features/news/news.service.ts`
- Create: `frontend/src/app/features/news/news-time.util.ts`
- Test: `frontend/src/app/features/news/news.service.spec.ts`
- Test: `frontend/src/app/features/news/news-time.util.spec.ts`

**Interfaces:**
- Consumes: backend contract from Task 5; `environment.apiBaseUrl`; `skipGlobalError()` from `core/errors/global-error.interceptor`.
- Produces: `interface NewsItem { sourceName: string; title: string; link: string; publishedAt: string }`; `interface NewsLatest { ukraine: NewsItem[]; world: NewsItem[] }`; `NewsService.latest(): Observable<NewsLatest>`; `interface TimeAgo { key: string; params?: { n: number } }`; `function timeAgo(publishedAt: string, now: Date): TimeAgo` returning i18n keys `news.timeAgo.justNow|minutes|hours|days`.

- [ ] **Step 1: Write the failing tests**

`news-time.util.spec.ts`:

```ts
import { timeAgo } from './news-time.util';

describe('timeAgo', () => {
  const now = new Date('2026-07-26T12:00:00Z');

  it('buckets into justNow / minutes / hours / days with translate params', () => {
    expect(timeAgo('2026-07-26T11:59:40Z', now)).toEqual({ key: 'news.timeAgo.justNow' });
    expect(timeAgo('2026-07-26T11:15:00Z', now)).toEqual({
      key: 'news.timeAgo.minutes',
      params: { n: 45 },
    });
    expect(timeAgo('2026-07-26T09:00:00Z', now)).toEqual({
      key: 'news.timeAgo.hours',
      params: { n: 3 },
    });
    expect(timeAgo('2026-07-23T12:00:00Z', now)).toEqual({
      key: 'news.timeAgo.days',
      params: { n: 3 },
    });
  });

  it('treats unparseable and future dates as justNow', () => {
    expect(timeAgo('not-a-date', now)).toEqual({ key: 'news.timeAgo.justNow' });
    expect(timeAgo('2026-07-26T12:30:00Z', now)).toEqual({ key: 'news.timeAgo.justNow' });
  });
});
```

`news.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { NewsService } from './news.service';
import { NewsLatest } from './news.models';

describe('NewsService', () => {
  let service: NewsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(NewsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs /news/latest and returns both lists', () => {
    const payload: NewsLatest = {
      ukraine: [
        {
          sourceName: 'ЕП',
          title: 'Заголовок',
          link: 'https://ua.example.com/1',
          publishedAt: '2026-07-26T09:00:00Z',
        },
      ],
      world: [],
    };
    let result: NewsLatest | undefined;

    service.latest().subscribe((r) => (result = r));
    const req = http.expectOne(`${environment.apiBaseUrl}/news/latest`);
    expect(req.request.method).toBe('GET');
    req.flush(payload);

    expect(result).toEqual(payload);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run (from `frontend/`): `npm run test:ci`
Expected: FAIL — cannot resolve `./news-time.util` / `./news.service`.

- [ ] **Step 3: Write the implementations**

`news.models.ts`:

```ts
/** Mirrors backend NewsItemResponse / NewsLatestResponse (feature 016, FR-4). */
export interface NewsItem {
  sourceName: string;
  title: string;
  link: string;
  publishedAt: string; // ISO instant
}

export interface NewsLatest {
  ukraine: NewsItem[];
  world: NewsItem[];
}
```

`news-time.util.ts`:

```ts
/** Translate-key + params pair: the component pipes it through ngx-translate (feature 016). */
export interface TimeAgo {
  key: string;
  params?: { n: number };
}

/**
 * Coarse relative age for a headline. Language-agnostic on purpose: it returns i18n keys
 * (news.timeAgo.*), so switching the UI language re-renders it correctly for free.
 * Unparseable or future dates read as "just now" - a headline must never show a negative age.
 */
export function timeAgo(publishedAt: string, now: Date): TimeAgo {
  const t = Date.parse(publishedAt);
  if (!Number.isFinite(t)) {
    return { key: 'news.timeAgo.justNow' };
  }
  const minutes = Math.floor((now.getTime() - t) / 60_000);
  if (minutes < 1) {
    return { key: 'news.timeAgo.justNow' };
  }
  if (minutes < 60) {
    return { key: 'news.timeAgo.minutes', params: { n: minutes } };
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return { key: 'news.timeAgo.hours', params: { n: hours } };
  }
  return { key: 'news.timeAgo.days', params: { n: Math.floor(hours / 24) } };
}
```

`news.service.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { skipGlobalError } from '../../core/errors/global-error.interceptor';
import { NewsLatest } from './news.models';

/**
 * Read access for the news widget (feature 016). skipGlobalError: a failed news fetch must never
 * toast - the widget silently hides instead (same pattern as BondSnapshotService.latest()).
 */
@Injectable({ providedIn: 'root' })
export class NewsService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  latest(): Observable<NewsLatest> {
    return this.http.get<NewsLatest>(`${this.base}/news/latest`, { context: skipGlobalError() });
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm run test:ci`
Expected: PASS (all suites, including the 2 new spec files).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/news
git commit -m "feat(news): frontend models, NewsService, timeAgo util (016 FR-5)"
```

---

### Task 7: Frontend `NewsWidgetComponent` + i18n keys

**Files:**
- Create: `frontend/src/app/features/news/news-widget.component.ts`
- Modify: `frontend/public/i18n/uk.json` (add `news` block)
- Modify: `frontend/public/i18n/en.json` (add `news` block)
- Test: `frontend/src/app/features/news/news-widget.component.spec.ts`

**Interfaces:**
- Consumes: `NewsService.latest()`, `timeAgo()`, `NewsItem`/`NewsLatest` from Task 6.
- Produces: standalone `NewsWidgetComponent` with selector `ig-news-widget`, no inputs/outputs — the tag Task 8 embeds. i18n keys `news.title`, `news.ukraine`, `news.world`, `news.timeAgo.justNow|minutes|hours|days`.

- [ ] **Step 1: Add the i18n keys**

In `frontend/public/i18n/uk.json`, add a top-level `"news"` block immediately BEFORE the existing
top-level `"notFound"` key (top-level keys are in feature order, not alphabetical; use the same
position in both files):

```json
  "news": {
    "title": "Фінансові новини",
    "ukraine": "Україна",
    "world": "Світові ринки",
    "timeAgo": {
      "justNow": "щойно",
      "minutes": "{{n}} хв тому",
      "hours": "{{n}} год тому",
      "days": "{{n}} дн тому"
    }
  },
```

In `frontend/public/i18n/en.json`:

```json
  "news": {
    "title": "Financial news",
    "ukraine": "Ukraine",
    "world": "World markets",
    "timeAgo": {
      "justNow": "just now",
      "minutes": "{{n}}m ago",
      "hours": "{{n}}h ago",
      "days": "{{n}}d ago"
    }
  },
```

- [ ] **Step 2: Write the failing test**

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { NewsWidgetComponent } from './news-widget.component';
import { NewsService } from './news.service';
import { NewsItem, NewsLatest } from './news.models';

function item(n: number): NewsItem {
  return {
    sourceName: `Src${n}`,
    title: `Headline ${n}`,
    link: `https://example.com/${n}`,
    publishedAt: '2026-07-26T09:00:00Z',
  };
}

describe('NewsWidgetComponent', () => {
  let latestSpy: jasmine.Spy;

  function create(payload?: NewsLatest, fail = false): ComponentFixture<NewsWidgetComponent> {
    latestSpy = fail
      ? jasmine.createSpy('latest').and.returnValue(throwError(() => new Error('down')))
      : jasmine.createSpy('latest').and.returnValue(of(payload));
    TestBed.configureTestingModule({
      imports: [NewsWidgetComponent, TranslateModule.forRoot()],
      providers: [{ provide: NewsService, useValue: { latest: latestSpy } }],
    });
    const fixture = TestBed.createComponent(NewsWidgetComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('renders both lists with outbound links (target=_blank rel=noopener)', () => {
    const fixture = create({ ukraine: [item(1)], world: [item(2)] });

    const links = fixture.nativeElement.querySelectorAll('a.ig-news__link');
    expect(links.length).toBe(2);
    expect(links[0].getAttribute('href')).toBe('https://example.com/1');
    expect(links[0].getAttribute('target')).toBe('_blank');
    expect(links[0].getAttribute('rel')).toBe('noopener');
    expect(fixture.nativeElement.textContent).toContain('Headline 1');
    expect(fixture.nativeElement.textContent).toContain('Src1');
  });

  it('shows at most 5 items per list', () => {
    const many = [1, 2, 3, 4, 5, 6, 7, 8].map(item);
    const fixture = create({ ukraine: many, world: [] });

    expect(fixture.nativeElement.querySelectorAll('a.ig-news__link').length).toBe(5);
  });

  it('renders nothing when the request fails', () => {
    const fixture = create(undefined, true);

    expect(fixture.nativeElement.querySelector('section')).toBeNull();
  });

  it('renders nothing when both lists are empty', () => {
    const fixture = create({ ukraine: [], world: [] });

    expect(fixture.nativeElement.querySelector('section')).toBeNull();
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `npm run test:ci`
Expected: FAIL — cannot resolve `./news-widget.component`.

- [ ] **Step 4: Write the implementation**

`news-widget.component.ts`:

```ts
import { isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  PLATFORM_ID,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { NewsService } from './news.service';
import { NewsItem } from './news.models';
import { TimeAgo, timeAgo } from './news-time.util';

interface NewsView extends NewsItem {
  ago: TimeAgo;
}

const SHOWN_PER_LIST = 5;

/**
 * Financial news widget (feature 016, FR-5). Self-contained: fetches on init (browser only - the
 * landing page is prerendered and must not fetch at build time), and on ANY failure or a fully
 * empty payload renders nothing at all. An engagement feature must never show a broken card.
 * Headlines stay in their source language by design; only chrome (title, section labels, relative
 * times) is translated.
 */
@Component({
  selector: 'ig-news-widget',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslateModule],
  template: `
    @if (state() === 'loading') {
      <section class="ig-news" aria-hidden="true">
        <div class="ig-news__skeleton"></div>
        <div class="ig-news__skeleton"></div>
      </section>
    } @else if (state() === 'ready') {
      <section class="ig-news" aria-labelledby="ig-news-title">
        <p class="ig-kicker">{{ 'news.title' | translate }}</p>
        <h2 id="ig-news-title" class="ig-sr-only">{{ 'news.title' | translate }}</h2>
        <div class="ig-news__cols">
          <div class="ig-news__col">
            <h3 class="ig-news__label">{{ 'news.ukraine' | translate }}</h3>
            <ul class="ig-news__list">
              @for (n of ukraine(); track n.link) {
                <li class="ig-news__item">
                  <a [href]="n.link" target="_blank" rel="noopener" class="ig-news__link">{{ n.title }}</a>
                  <span class="ig-news__meta ig-muted">
                    {{ n.sourceName }} &middot; {{ n.ago.key | translate: n.ago.params }}
                  </span>
                </li>
              }
            </ul>
          </div>
          <div class="ig-news__col">
            <h3 class="ig-news__label">{{ 'news.world' | translate }}</h3>
            <ul class="ig-news__list">
              @for (n of world(); track n.link) {
                <li class="ig-news__item">
                  <a [href]="n.link" target="_blank" rel="noopener" class="ig-news__link">{{ n.title }}</a>
                  <span class="ig-news__meta ig-muted">
                    {{ n.sourceName }} &middot; {{ n.ago.key | translate: n.ago.params }}
                  </span>
                </li>
              }
            </ul>
          </div>
        </div>
      </section>
    }
  `,
  styles: [
    `
      .ig-news { padding: 2rem 0 0; }
      .ig-news__cols { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
      .ig-news__label { margin: 0 0 0.75rem; font-size: 1rem; }
      .ig-news__list { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.75rem; }
      .ig-news__item { display: grid; gap: 0.15rem; }
      .ig-news__link { text-decoration: none; color: inherit; font-weight: 600; }
      .ig-news__link:hover { text-decoration: underline; }
      .ig-news__meta { font-size: 0.85rem; }
      .ig-news__skeleton {
        height: 1rem; margin: 0.5rem 0; border-radius: 4px;
        background: color-mix(in srgb, currentColor 12%, transparent);
      }
      @media (max-width: 640px) { .ig-news__cols { grid-template-columns: 1fr; } }
    `,
  ],
})
export class NewsWidgetComponent implements OnInit {
  private readonly newsService = inject(NewsService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<'loading' | 'ready' | 'hidden'>('loading');
  readonly ukraine = signal<NewsView[]>([]);
  readonly world = signal<NewsView[]>([]);

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      this.state.set('hidden'); // prerender: no network, no markup, no layout shift above the fold
      return;
    }
    this.newsService
      .latest()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          const now = new Date();
          const view = (items: NewsItem[]): NewsView[] =>
            items.slice(0, SHOWN_PER_LIST).map((i) => ({ ...i, ago: timeAgo(i.publishedAt, now) }));
          const ukraine = view(res.ukraine);
          const world = view(res.world);
          if (ukraine.length === 0 && world.length === 0) {
            this.state.set('hidden');
            return;
          }
          this.ukraine.set(ukraine);
          this.world.set(world);
          this.state.set('ready');
        },
        error: () => this.state.set('hidden'),
      });
  }
}
```

Note: if the repo has no `ig-sr-only` utility class, check `frontend/src/styles.css` for the
existing visually-hidden class name (`search.component.ts` uses `ig-sr-only`, so it exists) — keep
whatever name the stylesheet actually defines.

- [ ] **Step 5: Run tests + i18n dictionary check**

Run: `npm run test:ci`
Expected: PASS including the 4 new widget specs.

`ng test` never parses `public/i18n/*.json` (specs use `TranslateModule.forRoot()` with no HTTP
loader), so a malformed or uk/en-mismatched edit would ship green. Run the same parse + key-parity
check `scripts/verify-i18n.ps1` uses. From `frontend/`:

```bash
node -e "const fs=require('fs');const uk=JSON.parse(fs.readFileSync('public/i18n/uk.json','utf8'));const en=JSON.parse(fs.readFileSync('public/i18n/en.json','utf8'));function k(o,p){let r=[];for(const x in o){const v=o[x];const kp=p?p+'.'+x:x;if(v&&typeof v==='object')r=r.concat(k(v,kp));else r.push(kp);}return r;}const a=k(uk,'').sort(),b=k(en,'').sort();const onlyUk=a.filter(x=>!b.includes(x));const onlyEn=b.filter(x=>!a.includes(x));console.log('uk keys',a.length,'en keys',b.length);if(onlyUk.length||onlyEn.length){console.error('KEY MISMATCH onlyUk',onlyUk,'onlyEn',onlyEn);process.exit(1);}console.log('Key sets match.');"
```

Expected: `Key sets match.`

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/news frontend/public/i18n/uk.json frontend/public/i18n/en.json
git commit -m "feat(news): standalone news widget component with hide-on-error (016 FR-5)"
```

---

### Task 8: Embed the widget on landing + search; keep their specs green

**Files:**
- Modify: `frontend/src/app/features/landing/landing.component.ts` (import + template, after the pricing section ~line 157, before the `ig-l-disclaimer` paragraph)
- Modify: `frontend/src/app/features/search/search.component.ts` (import + template, after the results block at the end of the template)
- Modify: `frontend/src/app/features/landing/landing.component.spec.ts` (stub `NewsService`)
- Modify: `frontend/src/app/features/search/search.component.spec.ts` (stub `NewsService`)

**Interfaces:**
- Consumes: `NewsWidgetComponent` (selector `ig-news-widget`) and `NewsService` from Tasks 6–7.
- Produces: the user-visible feature; no new exports.

- [ ] **Step 1: Embed in `landing.component.ts`**

Add to the imports array of the `@Component` decorator: `NewsWidgetComponent`, with the TS import
`import { NewsWidgetComponent } from '../news/news-widget.component';`.

In the template, between the closing `</section>` of the pricing section and the
`<p class="ig-l-disclaimer">` paragraph, insert:

```html
    <ig-news-widget />
```

- [ ] **Step 2: Embed in `search.component.ts`**

Same TS/decorator import. In the template, after the results `@if` block (the final
`<section class="ig-card ig-results-wrap">...</section>` conditional), insert:

```html
    <ig-news-widget />
```

- [ ] **Step 3: Stub `NewsService` in both page specs**

In `landing.component.spec.ts` and `search.component.spec.ts`, add to the existing
`TestBed.configureTestingModule({ providers: [...] })` array:

```ts
{ provide: NewsService, useValue: { latest: () => of({ ukraine: [], world: [] }) } },
```

with imports `import { of } from 'rxjs';` (if absent) and
`import { NewsService } from '../news/news.service';`. Empty payload → the widget hides itself, so
every existing DOM assertion in those specs is untouched.

Then, in EACH of the two page specs, add one test proving the widget is actually embedded (without
it, deleting `<ig-news-widget />` from the template would keep the whole suite green — the host
element renders even in the hidden state, so this is non-flaky):

```ts
it('embeds the news widget', () => {
  expect((fixture.nativeElement as HTMLElement).querySelector('ig-news-widget')).toBeTruthy();
});
```

- [ ] **Step 4: Run the full frontend suite**

Run: `npm run test:ci`
Expected: PASS — all suites green, including untouched landing/search assertions.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/landing frontend/src/app/features/search
git commit -m "feat(news): embed news widget on landing and search pages (016 FR-5)"
```

---

### Task 9: Full verification + mandatory role sub-agent review

**Files:**
- No new files. Fixes discovered here are amended into the relevant modules with their own commits.

**Interfaces:** none — this is the CLAUDE.md-mandated completion gate.

- [ ] **Step 1: Full test suites**

Run: `cd backend && mvn -q test` — Expected: BUILD SUCCESS, zero failures.
Run: `cd frontend && npm run test:ci` — Expected: all green.

- [ ] **Step 2: Encoding check (CLAUDE.md)**

This feature must not touch any `.ps1`/`.cmd`/`.bat`. Verify:
`git diff --name-only main...HEAD | grep -Ei '\.(ps1|cmd|bat)$'` — Expected: no output. (If any
appear, scan them: `grep -rnP "[^\x00-\x7F]" <file>` must be clean.)

- [ ] **Step 3: Live smoke test (spec Acceptance #4)**

Start the local backend (`scripts/backend-run.ps1`) and the dev frontend (`ng serve` on 4200,
per the local-vs-Compose setup). In a browser: open `http://localhost:4200/` — the widget appears
with current headlines within a few seconds of the first load (first request seeds the cache; a
reload may be needed once). Open `/search` (login `dev@investguide.local` / `devpassword123`) —
widget present. Reload `/` within 30 min — backend log shows NO second `news_refresh` line.
If a default feed 403s/times out against the real network, swap it per Task 3 Step 1 and commit the
yml change.

- [ ] **Step 4: Mandatory role sub-agent review (CLAUDE.md — run without asking)**

Dispatch review sub-agents (at least two roles) over `git diff main...HEAD`:
- **QA**: test coverage vs spec Acceptance 1–4; hunt missed edge cases (feed with future dates,
  duplicate links across feeds, huge feed payloads).
- **BE lead**: concurrency of the single-flight guard, Mongo query efficiency, no secrets logged,
  security config change is minimal and correct.
- **FE lead**: OnPush correctness, prerender safety (no fetch during prerender), a11y of the list
  markup, i18n completeness (both locales have every `news.*` key).

Apply or explicitly rebut every finding; commit fixes as `fix(news): apply <role> review findings (016)`.

- [ ] **Step 5: Final commit / PR**

```bash
git push -u origin 016-financial-news-widget
```

Then open a PR `016-financial-news-widget` → `main` titled `feat(news): financial news widget (016)`
summarizing spec FRs and test evidence.
