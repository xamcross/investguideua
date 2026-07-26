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
 * unescaped, whitespace-collapsed and capped at {@value #MAX_TITLE_LENGTH} chars. A published date
 * after the fetch time (timezone slop, mangled years) is clamped to {@code fetchedAt} rather than
 * rejected, so such items don't pin to the top of every list forever. At most
 * {@value #MAX_ITEMS_PER_FEED} entries are taken per feed (RSS is newest-first by convention); the
 * whole feed body is already downloaded and Rome-parsed in full by this point, so the cap bounds
 * normalization/storage volume only (not download or parse cost) -- surplus entries are simply not
 * turned into {@link NewsItem}s and are not counted as rejected.
 */
public final class NewsFeedParser {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_ITEMS_PER_FEED = 50;

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
            if (items.size() == MAX_ITEMS_PER_FEED) {
                break;
            }
            String title = cleanTitle(entry.getTitle());
            String link = entry.getLink() == null ? "" : entry.getLink().trim();
            Date published = entry.getPublishedDate() != null
                    ? entry.getPublishedDate() : entry.getUpdatedDate();
            if (title.isBlank() || !isAbsoluteHttpUrl(link) || published == null) {
                rejected++;
                continue;
            }
            Instant publishedAt = published.toInstant();
            if (publishedAt.isAfter(fetchedAt)) {
                publishedAt = fetchedAt; // clamp timezone-slop/mangled-year dates, don't reject
            }
            items.add(new NewsItem(NewsItem.idFor(link), category, sourceName, title, link,
                    publishedAt, fetchedAt));
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
