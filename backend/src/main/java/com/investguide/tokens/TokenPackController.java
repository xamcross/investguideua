package com.investguide.tokens;

import com.investguide.tokens.dto.TokenPackResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Token-pack listing endpoint (SPECIFICATION §5.1 {@code /tokens/packs}, §9.1; ticket BE-T1).
 *
 * <p>Authenticated, read-only: lists only {@code active=true} packs for purchase (FE-PAY1).
 * Prices are integer minor units (kopiykas); the client handles display formatting.
 */
@RestController
@RequestMapping("/api/v1")
public class TokenPackController {

    private final TokenPackRepository tokenPackRepository;

    public TokenPackController(TokenPackRepository tokenPackRepository) {
        this.tokenPackRepository = tokenPackRepository;
    }

    @GetMapping("/tokens/packs")
    public List<TokenPackResponse> activePacks() {
        return tokenPackRepository.findByActiveTrue().stream()
                .map(TokenPackResponse::from)
                .toList();
    }
}
