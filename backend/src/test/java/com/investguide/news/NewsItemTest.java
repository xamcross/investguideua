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
