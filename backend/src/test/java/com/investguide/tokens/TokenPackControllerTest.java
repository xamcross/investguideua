package com.investguide.tokens;

import com.investguide.tokens.dto.TokenPackResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * BE-T1 DoD: {@code GET /tokens/packs} returns active packs with integer minor-unit prices; no
 * floating-point money in the type or serialization (SPECIFICATION §5.1, §9.1).
 */
class TokenPackControllerTest {

    private final TokenPackRepository repository = mock(TokenPackRepository.class);
    private final TokenPackController controller = new TokenPackController(repository);

    @Test
    void returnsActivePacksWithIntegerMinorUnitPrices() {
        when(repository.findByActiveTrue()).thenReturn(List.of(
                new TokenPack("pack-5", 5, 9_900L, "UAH", true),
                new TokenPack("pack-10", 10, 16_900L, "UAH", true)));

        List<TokenPackResponse> result = controller.activePacks();

        assertThat(result).extracting(TokenPackResponse::id).containsExactly("pack-5", "pack-10");
        // priceMinorUnits is a primitive long on both entity and DTO — no float money path.
        assertThat(result).extracting(TokenPackResponse::priceMinorUnits).containsExactly(9_900L, 16_900L);
        verify(repository).findByActiveTrue();
        verifyNoMoreInteractions(repository);
    }
}
