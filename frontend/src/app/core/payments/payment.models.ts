/** Payment contract types (mirror the backend BE-P DTOs). */

import { SearchCurrency } from '../investment/investment.models';

/** A purchasable token pack from GET /tokens/packs (BE-T1 / FE-PAY1). */
export interface TokenPack {
  id: string;
  tokens: number;
  /** Integer minor units (kopiykas); format for display only. */
  priceMinorUnits: number;
  currency: SearchCurrency;
}

/** POST /payments request body. */
export interface CreatePaymentRequest {
  packId: string;
}

/**
 * POST /payments response — the BE-P2 schema. The client redirects the buyer to `pageUrl` (the
 * monobank hosted checkout) and polls status by `paymentId` via GET /payments/{id}. No signature or
 * merchant secret is ever sent to the client (AC #10).
 */
export interface CreatePaymentResponse {
  paymentId: string;
  orderId: string;
  providerInvoiceId: string;
  pageUrl: string;
}

export type PaymentStatus = 'pending' | 'success' | 'failed' | 'reversed';

/** GET /payments/{id} response for status polling (BE-P5 / FE-PAY3). */
export interface PaymentStatusResponse {
  paymentId: string;
  orderId: string;
  packId: string;
  status: PaymentStatus;
  amountMinorUnits: number;
  currency: SearchCurrency;
  tokensToCredit: number;
}
