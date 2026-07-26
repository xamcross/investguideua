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
