package com.investguide.investment;

import com.investguide.catalog.Provider;
import com.investguide.catalog.ProviderCatalogService;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import com.investguide.config.AppProperties;
import com.investguide.config.LlmProperties;
import com.investguide.investment.dto.SearchRequestDto;
import com.investguide.investment.dto.SearchResponse;
import com.investguide.tokens.TokenLedgerService;
import com.investguide.user.User;
import com.investguide.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Investment-search orchestration (SPECIFICATION §4.2, §7.1–7.3, §7.6, §8, §11, §13 AC#2,#3; ticket
 * BE-S3). Implements the exact token-spend ordering; correctness comes from two deliberately ordered
 * single-document writes routed through {@link TokenLedgerService} — never a multi-doc transaction
 * (§7.6).
 *
 * <p>Ordering:
 * <ol>
 *   <li>Verified-email gate (BE-A6) and per-user rate limit (X5) <b>before</b> anything else — both
 *       return their error with <b>no token spent and no LLM call</b>.</li>
 *   <li>Validate input bounds (the configured {@code search.maxAmount}; structural validation already
 *       happened at the controller via bean validation).</li>
 *   <li>{@link TokenLedgerService#tryDebitOne} — 0 docs matched ⇒ {@code 402 INSUFFICIENT_TOKENS}, no
 *       LLM call (AC #2).</li>
 *   <li>Insert a {@code pending} {@link SearchRequest} ({@code tokenSpent=true}). If the insert fails,
 *       {@link TokenLedgerService#refundForInsertFailure} (the unguarded compensating +1) and abort
 *       (§4.2.3) — distinct from the post-insert refund path.</li>
 *   <li>Build the constrained prompt (BE-S4) from the pre-filtered catalog (BE-C3) and call the advisor
 *       (BE-S5); validate + enforce the output (BE-S6) with exactly one corrective retry on invalid
 *       JSON.</li>
 *   <li>Success ⇒ persist options + usage, {@code completed}, return §5.4 (live balance + disclaimers).</li>
 *   <li>Unrecoverable advisor failure ⇒ {@link TokenLedgerService#refundForFailedSearch} (status-guarded),
 *       {@code failed}, {@code 502 ADVISOR_UNAVAILABLE} (AC #3).</li>
 * </ol>
 */
@Service
public class InvestmentSearchService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentSearchService.class);

    private final UserRepository userRepository;
    private final SearchRequestRepository searchRequestRepository;
    private final TokenLedgerService tokenLedger;
    private final ProviderCatalogService catalogService;
    private final PromptBuilder promptBuilder;
    private final InvestmentAdvisorService advisor;
    private final AdvisorOutputParser outputParser;
    private final Disclaimers disclaimers;
    private final SearchRateLimiter rateLimiter;
    private final AppProperties appProperties;
    private final LlmProperties llmProperties;

    public InvestmentSearchService(UserRepository userRepository,
                                   SearchRequestRepository searchRequestRepository,
                                   TokenLedgerService tokenLedger,
                                   ProviderCatalogService catalogService,
                                   PromptBuilder promptBuilder,
                                   InvestmentAdvisorService advisor,
                                   AdvisorOutputParser outputParser,
                                   Disclaimers disclaimers,
                                   SearchRateLimiter rateLimiter,
                                   AppProperties appProperties,
                                   LlmProperties llmProperties) {
        this.userRepository = userRepository;
        this.searchRequestRepository = searchRequestRepository;
        this.tokenLedger = tokenLedger;
        this.catalogService = catalogService;
        this.promptBuilder = promptBuilder;
        this.advisor = advisor;
        this.outputParser = outputParser;
        this.disclaimers = disclaimers;
        this.rateLimiter = rateLimiter;
        this.appProperties = appProperties;
        this.llmProperties = llmProperties;
    }

    public SearchResponse search(String userId, SearchRequestDto request) {
        // (1) Verified-email gate (BE-A6) — precise error instead of a downstream INSUFFICIENT_TOKENS.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Session no longer valid."));
        if (!user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "Verify your email to run searches.");
        }

        // (1) Per-user rate limit (X5) — independent of balance; no token spent, no LLM call on trip.
        if (!rateLimiter.tryAcquire(userId)) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Too many searches. Please wait a moment and try again.");
        }

        // (2) Config-authoritative upper bound (lower bound + structure validated at the controller).
        if (request.amount() > appProperties.search().maxAmount()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "amount exceeds the maximum allowed.");
        }

        // (3) Guarded single-token debit. 0 docs matched -> 402, and crucially NO LLM call (AC #2).
        if (!tokenLedger.tryDebitOne(userId)) {
            throw new ApiException(ErrorCode.INSUFFICIENT_TOKENS, "You have no tokens left.");
        }

        // (4) Persist a pending request; on insert failure refund via the UNGUARDED compensating +1
        // (no document exists yet to status-guard against) and abort (§4.2.3).
        SearchInput input = SearchInput.from(request);
        SearchRequest saved;
        try {
            saved = searchRequestRepository.insert(SearchRequest.pending(userId, input));
        } catch (RuntimeException insertFailure) {
            tokenLedger.refundForInsertFailure(userId);
            log.warn("search_insert_failed userId={} - token refunded", userId);
            throw new ApiException(ErrorCode.INTERNAL, "Could not start the search. Please try again.");
        }

        // (5a) Pre-prompt catalog filter (BE-C3). No match -> refund the token (no LLM call, no value
        // delivered) and return an empty, un-charged result rather than charging for "no matches".
        List<Provider> allowed = catalogService.filterFor(input.amount(), input.currency().name());
        if (allowed.isEmpty()) {
            tokenLedger.refundForNoMatch(saved.getId(), userId);
            log.info("search_no_match reqId={} userId={} - token refunded", saved.getId(), userId);
            return emptyResult(saved, input);
        }

        try {
            List<InvestmentOption> options = runAdvisor(saved, input, allowed);
            return complete(saved, input, options);
        } catch (AdvisorUnavailableException | AdvisorOutputException failure) {
            // Persist any captured LLM usage for cost monitoring (X6) BEFORE the status-guarded refund.
            // The save leaves status=pending/tokenSpent=true untouched, so the §7.3 guard in
            // refundForFailedSearch still matches exactly one document.
            persistUsageOnFailure(saved);
            // (8) Post-insert failure: status-guarded refund (idempotent) + 502 (AC #3).
            tokenLedger.refundForFailedSearch(saved.getId(), userId);
            log.warn("search_failed reqId={} userId={} reason={}",
                    saved.getId(), userId, failure.getClass().getSimpleName());
            throw new ApiException(ErrorCode.ADVISOR_UNAVAILABLE,
                    "The advisor is unavailable right now. No token was charged.",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * Build the prompt from the (non-empty) pre-filtered catalog, call the advisor, and enforce the
     * output. The empty-catalog case is handled earlier in {@link #search} (refund + empty result), so
     * {@code allowed} is guaranteed non-empty here. Exactly one corrective retry on invalid output
     * (BE-S6); transport/timeout faults are not retried.
     */
    private List<InvestmentOption> runAdvisor(SearchRequest saved, SearchInput input,
                                              List<Provider> allowed) {
        Map<String, Provider> bySlug = new LinkedHashMap<>();
        for (Provider p : allowed) {
            bySlug.put(p.getId(), p);
        }

        int totalIn = 0;
        int totalOut = 0;
        AdvisorOutputException lastOutputError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            PromptBuilder.Prompt prompt = promptBuilder.build(input, allowed, attempt > 0);
            AdvisorResult result = advisor.advise(prompt.system(), prompt.user()); // may throw Unavailable
            totalIn += result.inputTokens();
            totalOut += result.outputTokens();
            try {
                List<InvestmentOption> options =
                        outputParser.parse(result.text(), bySlug, input.currency());
                saved.setLlmUsage(usage(totalIn, totalOut));
                return options;
            } catch (AdvisorOutputException outputError) {
                lastOutputError = outputError; // retry once, then fail.
            }
        }
        saved.setLlmUsage(usage(totalIn, totalOut));
        throw lastOutputError;
    }

    private SearchResponse complete(SearchRequest saved, SearchInput input,
                                    List<InvestmentOption> options) {
        saved.setOptions(options);
        saved.setStatus(SearchRequest.STATUS_COMPLETED);
        searchRequestRepository.save(saved);

        LlmUsage usage = saved.getLlmUsage();
        if (usage != null) {
            // X6: emit the per-search cost line for monitoring (also persisted on the request).
            log.info("search_completed reqId={} userId={} options={} inputTokens={} outputTokens={} costUsd={}",
                    saved.getId(), saved.getUserId(), options.size(),
                    usage.inputTokens(), usage.outputTokens(), String.format("%.6f", usage.costUsd()));
        }

        int liveBalance = userRepository.findById(saved.getUserId())
                .map(User::getTokenBalance)
                .orElse(0);
        String currencyRisk = disclaimers.currencyRiskOrNull(options, input.currency());
        return new SearchResponse(
                saved.getId(),
                liveBalance,
                input.amount(),
                input.currency(),
                options,
                disclaimers.standard(),
                currencyRisk);
    }

    /**
     * Build the §5.4 response for a no-match search whose token was already refunded
     * (BE-C3 / {@link TokenLedgerService#refundForNoMatch}): empty options, the standard disclaimer, and
     * the live (refunded) balance. The request document was flipped to {@code completed}/{@code
     * tokenSpent=false} by the ledger, so no further persistence is needed here.
     */
    private SearchResponse emptyResult(SearchRequest saved, SearchInput input) {
        int liveBalance = userRepository.findById(saved.getUserId())
                .map(User::getTokenBalance)
                .orElse(0);
        return new SearchResponse(
                saved.getId(),
                liveBalance,
                input.amount(),
                input.currency(),
                List.of(),
                disclaimers.standard(),
                null);
    }

    private LlmUsage usage(int inputTokens, int outputTokens) {
        return LlmUsage.of(inputTokens, outputTokens,
                llmProperties.inputUsdPerMillion(), llmProperties.outputUsdPerMillion());
    }

    /**
     * Persist captured LLM usage on a failed search so cost monitoring (X6) accounts for the tokens an
     * unrecoverable attempt still consumed. Best-effort: a save failure here must not mask the original
     * advisor failure nor block the refund, so it is swallowed (logged). No-op when no usage was captured
     * (e.g. a transport failure before any response). The doc is left {@code pending}/{@code tokenSpent}
     * so the subsequent §7.3 refund guard still matches.
     */
    private void persistUsageOnFailure(SearchRequest saved) {
        LlmUsage u = saved.getLlmUsage();
        if (u == null) {
            return;
        }
        try {
            searchRequestRepository.save(saved);
            log.info("search_failed_usage reqId={} userId={} inputTokens={} outputTokens={} costUsd={}",
                    saved.getId(), saved.getUserId(), u.inputTokens(), u.outputTokens(),
                    String.format("%.6f", u.costUsd()));
        } catch (RuntimeException ex) {
            log.warn("search_failed_usage_persist_skipped reqId={}", saved.getId());
        }
    }

    // ---- BE-S8: owner-scoped history reads ----------------------------------------------

    /**
     * Paginated, newest-first history for the caller (SPECIFICATION §4.4, §5.1; ticket BE-S8). The query
     * is owner-scoped, so a user only ever sees their own searches.
     */
    public com.investguide.investment.dto.HistoryPageResponse history(String userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        org.springframework.data.domain.Page<SearchRequest> result =
                searchRequestRepository.findByUserIdOrderByCreatedAtDesc(userId,
                        org.springframework.data.domain.PageRequest.of(safePage, safeSize));
        List<com.investguide.investment.dto.HistoryItemResponse> items = result.getContent().stream()
                .map(com.investguide.investment.dto.HistoryItemResponse::from)
                .toList();
        return com.investguide.investment.dto.HistoryPageResponse.from(result, items);
    }

    /**
     * A single past search, only if owned by the caller (SPECIFICATION §5.1; ticket BE-S8). A non-owned
     * or unknown id yields {@code 404 NOT_FOUND} without revealing whether the id exists. Rendered with
     * the same {@link SearchResponse} shape as a fresh search (disclaimers recomputed server-side).
     */
    public SearchResponse getOwned(String userId, String id) {
        SearchRequest req = searchRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Search not found."));
        List<InvestmentOption> options = req.getOptions() == null ? List.of() : req.getOptions();
        SearchCurrency currency = req.getInput().currency();
        int liveBalance = userRepository.findById(userId).map(User::getTokenBalance).orElse(0);
        return new SearchResponse(
                req.getId(),
                liveBalance,
                req.getInput().amount(),
                currency,
                options,
                disclaimers.standard(),
                disclaimers.currencyRiskOrNull(options, currency));
    }
}
