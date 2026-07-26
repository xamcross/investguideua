package com.investguide.news;

import com.investguide.config.NewsProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * JDK {@link HttpClient} feed fetch (feature 016) - repo convention, no third-party HTTP SDK.
 * Explicit User-Agent: several publishers reject the default Java UA. Feed URLs come exclusively
 * from configuration (no user input -> no SSRF surface). The entire exchange (connect + headers +
 * body) is time-bounded via {@code sendAsync(...).get(timeout)} - {@code HttpRequest.timeout(...)}
 * alone only bounds time-to-headers, so a slow-trickling body would otherwise hang the single-thread
 * refresh executor indefinitely. The size cap is enforced post-read; memory exposure is bounded by
 * what a {@code fetchTimeout}-sized window can deliver, capped again at {@value #MAX_FEED_BYTES}
 * bytes - a misbehaving feed must not balloon the heap.
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
        CompletableFuture<HttpResponse<byte[]>> exchange =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        HttpResponse<byte[]> response;
        try {
            response = exchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("Feed fetch failed for " + url, cause);
        } catch (TimeoutException ex) {
            exchange.cancel(true); // abandon the download - the client must not keep pulling bytes
            throw new IOException("Feed fetch exceeded " + timeout + " for " + url, ex);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Feed HTTP " + response.statusCode() + " from " + url);
        }
        byte[] body = response.body();
        if (body.length > MAX_FEED_BYTES) {
            throw new IOException("Feed body over " + MAX_FEED_BYTES + " bytes from " + url);
        }
        return body;
    }
}
