package com.investguide.news;

/** Fetches raw feed bytes (feature 016). Interface exists as the test seam for the refresh service. */
public interface NewsFeedFetcher {
    byte[] fetch(String url) throws Exception;
}
