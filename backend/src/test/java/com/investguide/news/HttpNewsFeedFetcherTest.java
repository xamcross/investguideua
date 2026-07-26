package com.investguide.news;

import com.investguide.config.NewsProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature 016 FR-3: {@link HttpNewsFeedFetcher} against a real ephemeral-port server (JDK
 * {@link HttpServer}, no new test dependency) -- happy path, non-2xx status, oversize body, and
 * fetch-timeout behavior.
 */
class HttpNewsFeedFetcherTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static NewsProperties properties(Duration fetchTimeout) {
        return new NewsProperties(Duration.ofMinutes(30), fetchTimeout, 20, 8,
                List.of(new NewsProperties.Feed("t", "http://x")),
                List.of(new NewsProperties.Feed("t", "http://x")));
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void happyPathReturnsExactBytes() throws Exception {
        byte[] body = "<rss><channel></channel></rss>".getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/feed", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        byte[] result = new HttpNewsFeedFetcher(properties(Duration.ofSeconds(5))).fetch(url("/feed"));

        assertThat(result).isEqualTo(body);
    }

    @Test
    void non2xxStatusThrowsIOException() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/feed", exchange -> exchange.sendResponseHeaders(404, -1));
        server.start();
        HttpNewsFeedFetcher fetcher = new HttpNewsFeedFetcher(properties(Duration.ofSeconds(5)));

        assertThatThrownBy(() -> fetcher.fetch(url("/feed"))).isInstanceOf(IOException.class);
    }

    @Test
    void oversizeBodyThrowsIOException() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/feed", exchange -> {
            exchange.sendResponseHeaders(200, 0); // 0 -> chunked transfer, length unbounded
            byte[] chunk = new byte[64 * 1024];
            long total = 0;
            long limit = (long) HttpNewsFeedFetcher.MAX_FEED_BYTES + 1;
            try (OutputStream os = exchange.getResponseBody()) {
                while (total < limit) {
                    os.write(chunk);
                    total += chunk.length;
                }
            }
        });
        server.start();
        HttpNewsFeedFetcher fetcher = new HttpNewsFeedFetcher(properties(Duration.ofSeconds(15)));

        assertThatThrownBy(() -> fetcher.fetch(url("/feed"))).isInstanceOf(IOException.class);
    }

    @Test
    void slowResponseTimesOut() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/feed", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "<rss></rss>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        HttpNewsFeedFetcher fetcher = new HttpNewsFeedFetcher(properties(Duration.ofMillis(500)));

        assertThatThrownBy(() -> fetcher.fetch(url("/feed"))).isInstanceOf(IOException.class);
    }

    @Test
    void slowTricklingBodyTimesOutViaExchangeGet() throws Exception {
        // Headers arrive immediately (so HttpRequest.timeout(), which only bounds time-to-headers,
        // cannot be what fires here); the body then stalls past fetchTimeout, so this specifically
        // exercises the sendAsync(...).get(timeout) branch (and its exchange.cancel(true) on timeout)
        // rather than the request-level timeout - the two are otherwise indistinguishable by an
        // IOException-only assertion.
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/feed", exchange -> {
            exchange.sendResponseHeaders(200, 0); // 0 -> chunked, headers flushed now
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("<rss>".getBytes(StandardCharsets.UTF_8));
                os.flush();
                Thread.sleep(2000); // body trickles in well past fetchTimeout below
                os.write("</rss>".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        HttpNewsFeedFetcher fetcher = new HttpNewsFeedFetcher(properties(Duration.ofMillis(500)));

        assertThatThrownBy(() -> fetcher.fetch(url("/feed"))).isInstanceOf(IOException.class);
    }
}
