package com.investguide.payments.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /payments} request body (SPECIFICATION §4.3.1, §9.2; ticket BE-P2).
 *
 * @param packId the {@code tokenPacks._id} slug (e.g. {@code pack-10}) to purchase.
 */
public record CreatePaymentRequest(@NotBlank String packId) {
}
