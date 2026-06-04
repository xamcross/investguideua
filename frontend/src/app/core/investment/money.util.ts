/**
 * Format an integer minor-unit (kopiyka/cent) amount for display only (FE-PAY1/FE-SEARCH2 rule:
 * never reconstruct prices via float math beyond display). e.g. 100000 UAH -> "1 000,00 UAH".
 * `currency` is the quote-currency code to append (UAH/USD/EUR); the minor units must already be in
 * that currency.
 */
export function formatMinorUnits(minor: number, currency: string): string {
  const major = minor / 100;
  const formatted = new Intl.NumberFormat('uk-UA', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(major);
  return `${formatted} ${currency}`;
}

/** Parse a user-entered UAH/USD major amount into integer minor units (kopiykas). */
export function toMinorUnits(major: number): number {
  return Math.round(major * 100);
}
