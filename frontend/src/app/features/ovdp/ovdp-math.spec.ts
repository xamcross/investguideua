import { grossIncomeKop, depositNetKop, ovdpNetKop } from './ovdp-math';

/**
 * 015-ovdp-data-pages: all calculator arithmetic is integer kopiykas (project money rule).
 * Reference figures mirror the worked examples in the deposit-ovdp-taxes article:
 * 23% total withholding on deposit interest (18% PIT + 5% military levy) => x0.77 net.
 */
describe('ovdp-math (integer kopiyka arithmetic)', () => {
  it('computes simple gross interest: 50 000 грн at 16% over 6 months = 4 000 грн', () => {
    expect(grossIncomeKop(5_000_000, 16, 6)).toBe(400_000);
  });

  it('computes gross interest over a full year: 100 000 грн at 17% = 17 000 грн', () => {
    expect(grossIncomeKop(10_000_000, 17, 12)).toBe(1_700_000);
  });

  it('always returns an integer even for awkward inputs', () => {
    const v = grossIncomeKop(100_001, 15.5, 12);
    expect(Number.isInteger(v)).toBeTrue();
  });

  it('deposit net applies the 23% withholding: 4 000 грн gross -> 3 080 грн net', () => {
    expect(depositNetKop(400_000)).toBe(308_000);
  });

  it('deposit net turns a 16% rate into ~12.32% on hand: 16 000 грн -> 12 320 грн', () => {
    expect(depositNetKop(1_600_000)).toBe(1_232_000);
  });

  it('OVDP net equals gross (tax-exempt)', () => {
    expect(ovdpNetKop(400_000)).toBe(400_000);
  });

  it('zero amount yields zero everywhere', () => {
    expect(grossIncomeKop(0, 16, 12)).toBe(0);
    expect(depositNetKop(0)).toBe(0);
  });
});
