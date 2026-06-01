package com.investguide.tokens.dto;

import com.investguide.tokens.TokenPack;

/**
 * Purchasable token pack view (SPECIFICATION §5.1 {@code /tokens/packs}, §9.1; tickets BE-T1,
 * FE-PAY1).
 *
 * <p>{@code priceMinorUnits} is exposed as the integer minor-unit (kopiyka) amount; the client
 * formats it for display (e.g. {@code 9900 -> "99,00 UAH"}) and must not reconstruct prices via
 * float math beyond display formatting (FE-PAY1). No floating-point money crosses the wire.
 */
public record TokenPackResponse(
        String id,
        int tokens,
        long priceMinorUnits,
        String currency
) {
    public static TokenPackResponse from(TokenPack p) {
        return new TokenPackResponse(p.getId(), p.getTokens(), p.getPriceMinorUnits(), p.getCurrency());
    }
}
