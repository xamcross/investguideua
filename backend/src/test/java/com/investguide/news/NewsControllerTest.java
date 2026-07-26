package com.investguide.news;

import com.investguide.common.security.JwtService;
import com.investguide.common.security.RestAuthEntryPoints;
import com.investguide.common.security.SecurityConfig;
import com.investguide.config.NewsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 016 FR-4: {@code GET /api/v1/news/latest} is anonymous-public, serves stored items only
 * (never a synchronous fetch), pins the exact cache header, and 200s with empty lists when the
 * collection is empty. Exercises the REAL {@link SecurityConfig} chain (mirrors
 * {@code BondPriceControllerTest}).
 */
@WebMvcTest(controllers = NewsController.class, properties = {
        "llm.api-key=test-key",
        "security.jwt-secret=test-secret-test-secret-test-secret-123456"
})
@Import({SecurityConfig.class, RestAuthEntryPoints.class, JwtService.class})
class NewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsItemRepository repository;

    @MockitoBean
    private NewsRefreshService refreshService;

    @MockitoBean
    private NewsProperties properties; // record: Mockito 5 inline mock-maker handles final types

    private static NewsItem item(String link, NewsCategory category) {
        return new NewsItem(NewsItem.idFor(link), category, "Src", "Title " + link, link,
                Instant.parse("2026-07-26T09:00:00Z"), Instant.parse("2026-07-26T10:00:00Z"));
    }

    @Test
    void anonymousGetReturnsBothListsWithExactCacheHeader() throws Exception {
        when(properties.servePerCategory()).thenReturn(8);
        when(repository.findByCategoryOrderByPublishedAtDesc(NewsCategory.UKRAINE, PageRequest.of(0, 8)))
                .thenReturn(List.of(item("https://ua.example.com/1", NewsCategory.UKRAINE)));
        when(repository.findByCategoryOrderByPublishedAtDesc(NewsCategory.WORLD, PageRequest.of(0, 8)))
                .thenReturn(List.of(item("https://w.example.com/1", NewsCategory.WORLD)));

        mockMvc.perform(get("/api/v1/news/latest"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$.ukraine[0].sourceName").value("Src"))
                .andExpect(jsonPath("$.ukraine[0].title").value("Title https://ua.example.com/1"))
                .andExpect(jsonPath("$.ukraine[0].link").value("https://ua.example.com/1"))
                .andExpect(jsonPath("$.ukraine[0].publishedAt").value("2026-07-26T09:00:00Z"))
                .andExpect(jsonPath("$.ukraine[0].id").doesNotExist())
                .andExpect(jsonPath("$.world[0].link").value("https://w.example.com/1"));

        verify(refreshService).maybeRefreshAsync();
    }

    @Test
    void emptyCollectionReturns200WithEmptyLists() throws Exception {
        when(properties.servePerCategory()).thenReturn(8);
        when(repository.findByCategoryOrderByPublishedAtDesc(NewsCategory.UKRAINE, PageRequest.of(0, 8)))
                .thenReturn(List.of());
        when(repository.findByCategoryOrderByPublishedAtDesc(NewsCategory.WORLD, PageRequest.of(0, 8)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/news/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ukraine").isEmpty())
                .andExpect(jsonPath("$.world").isEmpty());

        verify(refreshService).maybeRefreshAsync(); // empty store must still seed a refresh
    }
}
