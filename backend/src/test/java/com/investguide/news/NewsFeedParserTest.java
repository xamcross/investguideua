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

    @Test
    void fallsBackToUpdatedDateWhenPublishedDateMissing() {
        NewsFeedParser.Result result = NewsFeedParser.parse(
                fixture("atom-updated.xml"), NewsCategory.WORLD, "Atom", FETCHED_AT);

        assertThat(result.rejected()).isZero();
        assertThat(result.items()).hasSize(1);
        NewsItem item = result.items().get(0);
        assertThat(item.getTitle()).isEqualTo("Atom entry with updated date only");
        assertThat(item.getLink()).isEqualTo("https://atom.example.com/entry1");
        assertThat(item.getPublishedAt()).isEqualTo(Instant.parse("2026-07-25T06:00:00Z"));
    }

    @Test
    void clampsFuturePublishedDateToFetchedAt() {
        // Timezone slop / mangled years: a pubDate after fetchedAt must not pin the item forever.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>t</title>
                <item><title>Future dated item</title><link>https://x.example.com/future</link>
                <pubDate>Tue, 28 Jul 2026 10:00:00 GMT</pubDate></item>
                </channel></rss>""";

        NewsFeedParser.Result result = NewsFeedParser.parse(
                xml.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                NewsCategory.WORLD, "Future", FETCHED_AT);

        assertThat(result.rejected()).isZero();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getPublishedAt()).isEqualTo(FETCHED_AT);
    }

    @Test
    void capsItemsPerFeedAt50() {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>t</title>
                """);
        for (int i = 0; i < 60; i++) {
            xml.append("<item><title>Item ").append(i).append("</title>")
                    .append("<link>https://many.example.com/").append(i).append("</link>")
                    .append("<pubDate>Sat, 25 Jul 2026 08:30:00 GMT</pubDate></item>\n");
        }
        xml.append("</channel></rss>");

        NewsFeedParser.Result result = NewsFeedParser.parse(
                xml.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                NewsCategory.WORLD, "Many", FETCHED_AT);

        assertThat(result.items()).hasSize(50);
        assertThat(result.rejected()).isZero();
    }

    @Test
    void htmlBodyThrowsNewsFeedException() {
        // A misconfigured URL that 200s with HTML must fail loudly, not silently parse to 0 items.
        String html = "<!doctype html><html><head><title>x</title></head><body><p>not a feed</p></body></html>";

        assertThatThrownBy(() -> NewsFeedParser.parse(
                html.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                NewsCategory.WORLD, "Html", FETCHED_AT))
                .isInstanceOf(NewsFeedException.class);
    }

    @Test
    void title200CharsPassesThroughUnmodified() {
        String title200 = "a".repeat(200);
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>t</title>
                <item><title>%s</title><link>https://x.example.com/200</link>
                <pubDate>Sat, 25 Jul 2026 08:30:00 GMT</pubDate></item>
                </channel></rss>""".formatted(title200);

        NewsFeedParser.Result result = NewsFeedParser.parse(
                xml.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                NewsCategory.WORLD, "Exact200", FETCHED_AT);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getTitle()).isEqualTo(title200);
    }

    @Test
    void title201CharsIsTruncatedTo200OrFewer() {
        String title201 = "b".repeat(201);
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>t</title>
                <item><title>%s</title><link>https://x.example.com/201</link>
                <pubDate>Sat, 25 Jul 2026 08:30:00 GMT</pubDate></item>
                </channel></rss>""".formatted(title201);

        NewsFeedParser.Result result = NewsFeedParser.parse(
                xml.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                NewsCategory.WORLD, "Over201", FETCHED_AT);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getTitle().length()).isLessThanOrEqualTo(200);
    }
}
