// Exact decimal -> integer minor-unit conversion (feature 009).
//
// Money is integer minor units (kopiykas/cents); floats are prohibited. PrivatBank quotes prices as
// decimals (e.g. 1076.58 per 1000 face value); we convert to minor units using STRING/INTEGER math
// (BigInt) so there is no IEEE-754 drift (1076.58 * 100 is NOT exactly 107658 in floating point).
//
// Rounding: values with more than 2 fraction digits are rounded half-up at the 2nd decimal. A
// missing/empty/non-numeric value throws (the caller must reject that record, never store 0).

/**
 * Convert a price expressed in major units (number or numeric string) to integer minor units.
 * @param {number|string} value e.g. 1076.58 or "1076.58"
 * @returns {number} integer minor units, e.g. 107658
 */
export function toMinorUnits(value) {
  if (value === null || value === undefined || value === '') {
    throw new Error('missing price value');
  }
  const s = String(value).trim();
  if (!/^-?\d+(\.\d+)?$/.test(s)) {
    throw new Error(`invalid numeric price: ${JSON.stringify(value)}`);
  }

  const negative = s.startsWith('-');
  const abs = negative ? s.slice(1) : s;
  const [intPart, fracRaw = ''] = abs.split('.');

  let minor;
  if (fracRaw.length > 2) {
    const kept = fracRaw.slice(0, 2);
    const nextDigit = fracRaw.charCodeAt(2) - 48; // '0' === 48
    minor = BigInt(intPart) * 100n + BigInt(kept);
    if (nextDigit >= 5) {
      minor += 1n; // round half up
    }
  } else {
    const kept = fracRaw.padEnd(2, '0'); // '' -> '00', '5' -> '50'
    minor = BigInt(intPart) * 100n + BigInt(kept);
  }

  const result = negative ? -minor : minor;
  return Number(result);
}
