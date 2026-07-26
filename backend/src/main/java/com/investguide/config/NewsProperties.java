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
 *
 * <p><b>Invariant:</b> feed links must not overlap across categories -- {@code _id} is the link
 * hash, so a cross-category duplicate would silently reassign the item's category to whichever
 * category is processed last.
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
