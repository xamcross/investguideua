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
 * JDK {@link HttpClient} feed fetch (feature 016) - repo convention, no third-party HTTP SDK.
 * Explicit User-Agent: several publishers reject the default Java UA. Feed URLs come exclusively
 * from configuration (no user input -> no SSRF surface). Responses are streamed and capped at
 * {@value #MAX_FEED_BYTES} bytes - a misbehaving feed must not balloon the heap.
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
