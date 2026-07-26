package com.investguide.news;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Single-thread daemon executor for async news refresh (feature 016, FR-3). */
@Configuration
public class NewsConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService newsRefreshExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "news-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }
}
