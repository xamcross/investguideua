/**
 * Calculator arithmetic for the /ovdp data pages (feature 015).
 *
 * All money values are integer kopiykas (project rule: money is always integer minor units).
 * Each function rounds exactly once, at its final step, so composed results stay integers.
 */

/**
 * Simple (non-compounding) gross interest for {@code amountKop} at {@code ratePct} per annum over
 * {@code termMonths}. Simple interest mirrors how deposit/OVDP offers quote "X% річних" for a
 * fixed term; compounding is out of scope for an orientation calculator.
 */
export function grossIncomeKop(amountKop: number, ratePct: number, termMonths: number): number {
  return Math.round((amountKop * ratePct * termMonths) / (100 * 12));
}

/**
 * Net deposit income after the statutory 23% withholding on interest
 * (18% податку на доходи фізичних осіб + 5% військового збору) - i.e. x0.77.
 */
export function depositNetKop(grossKop: number): number {
  return Math.round((grossKop * 77) / 100);
}

/** OVDP coupon/redemption income is tax-exempt: net equals gross. */
export function ovdpNetKop(grossKop: number): number {
  return grossKop;
}
