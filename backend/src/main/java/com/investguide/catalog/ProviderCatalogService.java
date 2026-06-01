package com.investguide.catalog;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Catalog read + pre-prompt filtering (SPECIFICATION §6, §8.3; tickets BE-C2/BE-C3).
 *
 * <p>{@link #filterFor(long, String)} produces the LLM's <i>allowed option set</i>: the active
 * catalog narrowed to providers plausibly relevant to a request, applied <b>before</b> prompting
 * so the model only ever sees options it could legitimately recommend (anti-hallucination, §8.3).
 *
 * <p>Filtering rules (both must hold for a provider to survive):
 * <ul>
 *   <li><b>Amount fits:</b> {@code provider.minAmount <= requestedAmount}. Amounts are integer
 *       minor units (kopiykas/cents) on both sides — the caller (BE-S) passes the validated
 *       request amount in the same convention {@link Provider} stores. {@code maxAmount}, when
 *       present, must also be {@code >= requestedAmount}.</li>
 *   <li><b>Currency supported:</b> {@code provider.currencies} contains the requested currency
 *       (compared case-insensitively).</li>
 * </ul>
 *
 * <p>An empty result is a valid outcome, not an error: per BE-C3/BE-S, search then returns an
 * empty {@code options} set rather than failing. The returned list is never {@code null}.
 */
@Service
public class ProviderCatalogService {

    private final ProviderRepository providerRepository;

    public ProviderCatalogService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    /** Active catalog, unfiltered (backs {@code GET /providers} and is the source for filtering). */
    public List<Provider> activeProviders() {
        return providerRepository.findByActiveTrue();
    }

    /**
     * The pre-prompt allowed-option set for a search request.
     *
     * @param amount   requested amount in integer minor units (kopiykas/cents); must be {@code > 0}
     * @param currency requested currency code (e.g. {@code "UAH"}); compared case-insensitively
     * @return active providers that accept this amount and currency; possibly empty, never null
     */
    public List<Provider> filterFor(long amount, String currency) {
        String wanted = normalizeCurrency(currency);
        return providerRepository.findByActiveTrue().stream()
                .filter(p -> accepts(p, amount, wanted))
                .toList();
    }

    private static boolean accepts(Provider p, long amount, String wantedCurrency) {
        if (p.getMinAmount() > amount) {
            return false;
        }
        if (p.getMaxAmount() != null && p.getMaxAmount() < amount) {
            return false;
        }
        List<String> currencies = p.getCurrencies();
        if (currencies == null) {
            return false;
        }
        return currencies.stream()
                .filter(Objects::nonNull)
                .map(ProviderCatalogService::normalizeCurrency)
                .anyMatch(c -> c.equals(wantedCurrency));
    }

    private static String normalizeCurrency(String currency) {
        return currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
    }
}
