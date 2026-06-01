package com.investguide.investment;

import com.investguide.investment.dto.HistoryPageResponse;
import com.investguide.investment.dto.SearchRequestDto;
import com.investguide.investment.dto.SearchResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Investment search + history endpoints (SPECIFICATION §5.1; tickets BE-S3, BE-S8).
 *
 * <p>All routes are protected (the security chain requires a valid access token, so an unauthenticated
 * call {@code 401}s before reaching a handler). The principal is the userId set by
 * {@code JwtAuthenticationFilter}. Structural request validation is declarative ({@code @Valid} on the
 * body → aggregated {@code 400 VALIDATION_ERROR}); all business ordering, rate limiting, the verified
 * gate, token spend and refunds live in {@link InvestmentSearchService}.
 */
@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private final InvestmentSearchService searchService;

    public SearchController(InvestmentSearchService searchService) {
        this.searchService = searchService;
    }

    /** §5.1 {@code POST /investments/search} — runs the LLM search; spends 1 token (BE-S3). */
    @PostMapping("/investments/search")
    public SearchResponse search(@AuthenticationPrincipal String userId,
                                 @Valid @RequestBody SearchRequestDto request) {
        return searchService.search(userId, request);
    }

    /** §5.1 {@code GET /investments/history} — owner-scoped, newest-first, paginated (BE-S8). */
    @GetMapping("/investments/history")
    public HistoryPageResponse history(@AuthenticationPrincipal String userId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return searchService.history(userId, page, size);
    }

    /**
     * §5.1 {@code GET /investments/{id}} — a single search, owner-only (BE-S8). A non-owned/unknown id
     * returns {@code 404} (existence is not revealed). Declared after {@code /investments/history} is
     * irrelevant to routing (distinct literal path), but kept last for readability.
     */
    @GetMapping("/investments/{id}")
    public SearchResponse byId(@AuthenticationPrincipal String userId,
                               @PathVariable String id) {
        return searchService.getOwned(userId, id);
    }
}
