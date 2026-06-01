package com.investguide.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BE-C3 DoD: pre-prompt filtering drops providers whose minAmount exceeds the requested amount or
 * whose currencies don't include the requested currency; an empty result is valid (SPECIFICATION
 * §6, §8.3). Amounts are integer minor units (kopiykas).
 */
class ProviderCatalogServiceTest {

    private final ProviderRepository repository = mock(ProviderRepository.class);
    private final ProviderCatalogService service = new ProviderCatalogService(repository);

    private static Provider provider(String id, long minAmount, Long maxAmount, List<String> currencies) {
        return new Provider(id, id, ProviderCategory.BANK_DEPOSIT, "desc",
                minAmount, maxAmount, currencies, new ReturnRange(10.0, 12.0),
                RiskLevel.LOW, "https://example.test", true);
    }

    @Test
    void excludesProviderWhoseMinAmountExceedsRequested() {
        Provider affordable = provider("affordable", 100_000L, null, List.of("UAH"));   // min 1000 UAH
        Provider tooHigh = provider("too-high", 10_000_000L, null, List.of("UAH"));      // min 100000 UAH
        when(repository.findByActiveTrue()).thenReturn(List.of(affordable, tooHigh));

        List<Provider> result = service.filterFor(5_000_000L, "UAH"); // 50000 UAH

        assertThat(result).extracting(Provider::getId).containsExactly("affordable");
    }

    @Test
    void excludesProviderOnCurrencyMismatch() {
        Provider uahOnly = provider("uah-only", 0L, null, List.of("UAH"));
        Provider usdCapable = provider("usd-ok", 0L, null, List.of("UAH", "USD"));
        when(repository.findByActiveTrue()).thenReturn(List.of(uahOnly, usdCapable));

        List<Provider> result = service.filterFor(100_000L, "USD");

        assertThat(result).extracting(Provider::getId).containsExactly("usd-ok");
    }

    @Test
    void currencyComparisonIsCaseInsensitive() {
        Provider p = provider("p", 0L, null, List.of("uah"));
        when(repository.findByActiveTrue()).thenReturn(List.of(p));

        assertThat(service.filterFor(100_000L, "UAH")).hasSize(1);
    }

    @Test
    void respectsMaxAmountUpperBound() {
        Provider capped = provider("capped", 0L, 1_000_000L, List.of("UAH")); // max 10000 UAH
        when(repository.findByActiveTrue()).thenReturn(List.of(capped));

        assertThat(service.filterFor(5_000_000L, "UAH")).isEmpty();          // 50000 UAH > cap
        assertThat(service.filterFor(500_000L, "UAH")).hasSize(1);           // 5000 UAH <= cap
    }

    @Test
    void emptyFilteredSetIsReturnedNotAnError() {
        Provider tooHigh = provider("too-high", 10_000_000L, null, List.of("UAH"));
        when(repository.findByActiveTrue()).thenReturn(List.of(tooHigh));

        assertThat(service.filterFor(1_000L, "UAH")).isEmpty();
    }

    @Test
    void boundaryMinAmountEqualToRequestedIsIncluded() {
        Provider exact = provider("exact", 100_000L, null, List.of("UAH"));
        when(repository.findByActiveTrue()).thenReturn(List.of(exact));

        assertThat(service.filterFor(100_000L, "UAH")).hasSize(1);
    }
}
