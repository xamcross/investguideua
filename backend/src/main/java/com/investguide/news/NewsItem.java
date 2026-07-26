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
 * repeated fetches upsert instead of duplicating. Headline + attribution + link only -- no article
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
