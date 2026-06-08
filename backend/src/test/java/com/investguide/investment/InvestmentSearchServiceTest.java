package com.investguide.investment;

import com.investguide.catalog.Provider;
import com.investguide.catalog.ProviderCatalogService;
import com.investguide.catalog.ProviderCategory;
import com.investguide.catalog.ReturnRange;
import com.investguide.catalog.RiskLevel;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import com.investguide.investment.dto.SearchRequestDto;
import com.investguide.investment.dto.SearchResponse;
import com.investguide.tokens.TokenLedgerService;
import com.investguide.user.User;
import com.investguide.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link InvestmentSearchService} — the token-spend ordering and failure/refund paths
 * (SPECIFICATION §4.2, §7.1–7.3; QA1 AC #2, #3, #5). All collaborators are mocked, so no Mongo/LLM is
 * required; the assertions target the exact ordering guarantees (no LLM call on 402, refund on failure,
 * disclaimer always present on success).
 */
class InvestmentSearchServiceTest {

    private static final String USER = "user-1";

    private UserRepository userRepository;
    private SearchRequestRepository searchRequestRepository;
    private TokenLedgerService ledger;
    private ProviderCatalogService catalogService;
    private PromptBuilder promptBuilder;
    private InvestmentAdvisorService advisor;
    private AdvisorOutputParser outputParser;
    private SearchRateLimiter rateLimiter;
    private InvestmentSearchService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        searchRequestRepository = mock(SearchRequestRepository.class);
        ledger = mock(TokenLedgerService.class);
        catalogService = mock(ProviderCatalogService.class);
        promptBuilder = mock(PromptBuilder.class);
        advisor = mock(InvestmentAdvisorService.class);
        outputParser = mock(AdvisorOutputParser.class);
        rateLimiter = mock(SearchRateLimiter.class);
        service = new InvestmentSearchService(
                userRepository, searchRequestRepository, ledger, catalogService, promptBuilder,
                advisor, outputParser, new Disclaimers(), rateLimiter,
                InvestmentTestFixtures.appProperties(100_000_000L, 5, 5),
                InvestmentTestFixtures.llmProperties(3000, 40.0));
    }

    private static SearchRequestDto request() {
        return new SearchRequestDto(500_000L, SearchCurrency.UAH, InvestmentHorizon.MEDIUM,
                RiskLevel.MODERATE, "стабільний дохід", SearchLanguage.UK);
    }

    private static User verifiedUser(int balance) {
        User u = new User();
        u.setId(USER);
        u.setEmailVerified(true);
        u.setTokenBalance(balance);
        return u;
    }

    private void stubVerifiedUser(int balance) {
        when(userRepository.findById(USER)).thenReturn(Optional.of(verifiedUser(balance)));
    }

    private void stubInsertReturnsId(String id) {
        when(searchRequestRepository.insert(any(SearchRequest.class))).thenAnswer(inv -> {
            SearchRequest r = inv.getArgument(0);
            r.setId(id);
            return r;
        });
    }

    private static Provider privat() {
        return new Provider("privatbank", "PrivatBank", ProviderCategory.GOV_BOND, "d",
                100_000L, null, List.of("UAH", "USD"), new ReturnRange(13, 15), RiskLevel.LOW,
                "https://privatbank.ua", true);
    }

    // ---- AC #2: balance 0 -> 402, no LLM call -------------------------------------------

    @Test
    void search_zeroBalance_returns402_andMakesNoLlmCall() {
        stubVerifiedUser(0);
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(ledger.tryDebitOne(USER)).thenReturn(false); // 0-balance guard matches nothing

        assertThatThrownBy(() -> service.search(USER, request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.INSUFFICIENT_TOKENS);

        verify(advisor, never()).advise(anyString(), anyString());
        verify(searchRequestRepository, never()).insert(any(SearchRequest.class));
    }

    // ---- AC #3: advisor output failure -> refund (status-guarded) + 502 -----------------

    @Test
    void search_invalidOutputTwice_refundsToken_andReturns502_withRetry() {
        stubVerifiedUser(4);
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(ledger.tryDebitOne(USER)).thenReturn(true);
        stubInsertReturnsId("req-1");
        when(catalogService.filterFor(eq(500_000L), eq("UAH"))).thenReturn(List.of(privat()));
        when(promptBuilder.build(any(), anyList(), anyBoolean()))
                .thenReturn(new PromptBuilder.Prompt("sys", "usr"));
        when(advisor.advise("sys", "usr")).thenReturn(new AdvisorResult("garbage", 100, 50));
        when(outputParser.parse(any(), any(), any()))
                .thenThrow(new AdvisorOutputException("invalid")); // both attempts fail

        assertThatThrownBy(() -> service.search(USER, request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.ADVISOR_UNAVAILABLE);

        // Exactly one corrective retry, then the status-guarded refund (token returned).
        verify(advisor, times(2)).advise("sys", "usr");
        verify(ledger).refundForFailedSearch("req-1", USER);
        verify(ledger, never()).refundForInsertFailure(anyString());
    }

    @Test
    void search_advisorUnavailable_notRetried_refundsAndReturns502() {
        stubVerifiedUser(4);
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(ledger.tryDebitOne(USER)).thenReturn(true);
        stubInsertReturnsId("req-2");
        when(catalogService.filterFor(eq(500_000L), eq("UAH"))).thenReturn(List.of(privat()));
        when(promptBuilder.build(any(), anyList(), anyBoolean()))
                .thenReturn(new PromptBuilder.Prompt("sys", "usr"));
        when(advisor.advise("sys", "usr")).thenThrow(new AdvisorUnavailableException("timeout"));

        assertThatThrownBy(() -> service.search(USER, request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.ADVISOR_UNAVAILABLE);

        verify(advisor, times(1)).advise("sys", "usr"); // transport faults are not retried
        verify(ledger).refundForFailedSearch("req-2", USER);
    }

    // ---- AC #5 + happy path: completed, options + disclaimer + live balance --------------

    @Test
    void search_success_completes_withOptionsDisclaimerAndLiveBalance() {
        stubVerifiedUser(4); // balance after debit
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(ledger.tryDebitOne(USER)).thenReturn(true);
        stubInsertReturnsId("req-3");
        when(catalogService.filterFor(eq(500_000L), eq("UAH"))).thenReturn(List.of(privat()));
        when(promptBuilder.build(any(), anyList(), anyBoolean()))
                .thenReturn(new PromptBuilder.Prompt("sys", "usr"));
        when(advisor.advise("sys", "usr")).thenReturn(new AdvisorResult("{json}", 200, 100));
        InvestmentOption opt = new InvestmentOption("privatbank", "PrivatBank", "ОВДП",
                ProviderCategory.GOV_BOND, SearchCurrency.UAH, new ReturnRange(13, 15),
                RiskLevel.LOW, 100_000L, "строк", "ок", "https://privatbank.ua", null, null, null, null);
        when(outputParser.parse(any(), any(), eq(SearchCurrency.UAH))).thenReturn(List.of(opt));
        when(searchRequestRepository.save(any(SearchRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        SearchResponse response = service.search(USER, request());

        assertThat(response.requestId()).isEqualTo("req-3");
        assertThat(response.tokenBalance()).isEqualTo(4);
        assertThat(response.options()).hasSize(1);
        assertThat(response.disclaimer()).isEqualTo(Disclaimers.STANDARD); // AC #5: always present
        assertThat(response.currencyRiskDisclaimer()).isNull(); // all options in requested currency
        verify(ledger, never()).refundForFailedSearch(anyString(), anyString());
    }

    @Test
    void search_currencyMismatch_addsCurrencyRiskDisclaimer() {
        stubVerifiedUser(4);
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(ledger.tryDebitOne(USER)).thenReturn(true);
        stubInsertReturnsId("req-4");
        when(catalogService.filterFor(eq(500_000L), eq("UAH"))).thenReturn(List.of(privat()));
        when(promptBuilder.build(any(), anyList(), anyBoolean()))
                .thenReturn(new PromptBuilder.Prompt("sys", "usr"));
        when(advisor.advise("sys", "usr")).thenReturn(new AdvisorResult("{json}", 10, 10));
        InvestmentOption usdOpt = new InvestmentOption("privatbank", "PrivatBank", "USD bond",
                ProviderCategory.GOV_BOND, SearchCurrency.USD, new ReturnRange(3, 5),
                RiskLevel.LOW, 100_000L, "term", "ok", "https://privatbank.ua", null, null, null, null);
        when(outputParser.parse(any(), any(), eq(SearchCurrency.UAH))).thenReturn(List.of(usdOpt));
        when(searchRequestRepository.save(any(SearchRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        SearchResponse response = service.search(USER, request());

        assertThat(response.currencyRiskDisclaimer()).isEqualTo(Disclaimers.CURRENCY_RISK);
    }

    // ---- BE-C3: empty catalog match -> empty result, no LLM call, token REFUNDED ----------

    @Test
    void search_emptyCatalogMatch_refundsToken_returnsEmptyOptions_noLlmCall() {
        stubVerifiedUser(5); // balance read reflects the refund (back to pre-debit)
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(ledger.tryDebitOne(USER)).thenReturn(true);
        stubInsertReturnsId("req-5");
        when(catalogService.filterFor(eq(500_000L), eq("UAH"))).thenReturn(List.of()); // nothing matches

        SearchResponse response = service.search(USER, request());

        // Empty filtered set -> empty options, NOT an error; no LLM call; the token is REFUNDED.
        assertThat(response.options()).isEmpty();
        assertThat(response.disclaimer()).isEqualTo(Disclaimers.STANDARD); // AC #5 still holds
        assertThat(response.tokenBalance()).isEqualTo(5);                  // refunded balance
        verify(advisor, never()).advise(anyString(), anyString());
        verify(ledger).refundForNoMatch("req-5", USER);
        verify(ledger, never()).refundForFailedSearch(anyString(), anyString());
    }

    // ---- guards: rate limit + verified email, both before any spend ---------------------

    @Test
    void search_rateLimited_returns429_noDebit_noLlm() {
        stubVerifiedUser(5);
        when(rateLimiter.tryAcquire(USER)).thenReturn(false);

        assertThatThrownBy(() -> service.search(USER, request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.RATE_LIMITED);

        verify(ledger, never()).tryDebitOne(anyString());
        verify(advisor, never()).advise(anyString(), anyString());
    }

    @Test
    void search_unverifiedEmail_returns403_noDebit() {
        User unverified = new User();
        unverified.setId(USER);
        unverified.setEmailVerified(false);
        when(userRepository.findById(USER)).thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> service.search(USER, request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

        verify(ledger, never()).tryDebitOne(anyString());
        verify(advisor, never()).advise(anyString(), anyString());
    }

    // ---- §4.2.3: debit succeeded but insert failed -> compensating unguarded refund ------

    @Test
    void search_insertFailure_refundsViaInsertFailurePath_andAborts() {
        stubVerifiedUser(4);
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(ledger.tryDebitOne(USER)).thenReturn(true);
        when(searchRequestRepository.insert(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("mongo down"));

        assertThatThrownBy(() -> service.search(USER, request()))
                .isInstanceOf(ApiException.class);

        verify(ledger).refundForInsertFailure(USER);          // unguarded +1 (no doc to status-guard)
        verify(ledger, never()).refundForFailedSearch(anyString(), anyString());
        verify(advisor, never()).advise(anyString(), anyString());
    }
}
