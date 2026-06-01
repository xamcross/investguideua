package com.investguide.catalog;

import com.investguide.catalog.dto.ProviderResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * BE-C2 DoD: {@code GET /providers} returns only active providers, read-only (SPECIFICATION §5.1).
 *
 * <p>Active-only is guaranteed by delegating to {@code findByActiveTrue()} (never {@code findAll});
 * read-only is structural — the controller exposes no write mapping. This test pins both.
 */
class ProviderControllerTest {

    private final ProviderRepository repository = mock(ProviderRepository.class);
    private final ProviderController controller = new ProviderController(repository);

    @Test
    void returnsActiveCatalogMappedToDto() {
        Provider p = new Provider("privatbank", "ПриватБанк", ProviderCategory.BANK_DEPOSIT,
                "desc", 100_000L, null, List.of("UAH", "USD"),
                new ReturnRange(13.0, 16.5), RiskLevel.LOW, "https://privatbank.ua/ovdp", true);
        when(repository.findByActiveTrue()).thenReturn(List.of(p));

        List<ProviderResponse> result = controller.activeProviders();

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo("privatbank");
            assertThat(r.category()).isEqualTo(ProviderCategory.BANK_DEPOSIT);
            assertThat(r.currencies()).containsExactly("UAH", "USD");
            assertThat(r.minAmount()).isEqualTo(100_000L);
            assertThat(r.sourceUrl()).isEqualTo("https://privatbank.ua/ovdp");
        });
        // Active-only source; no other repository (write) interaction.
        verify(repository).findByActiveTrue();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptyCatalogReturnsEmptyList() {
        when(repository.findByActiveTrue()).thenReturn(List.of());
        assertThat(controller.activeProviders()).isEmpty();
    }
}
