import { mapErrorToUx } from './error-ux.util';
import { ParsedApiError } from '../api/api-error.util';

/**
 * QA2 critical flow: 402 / 502 / 429 (and friends) UX. {@link mapErrorToUx} is the single source of
 * truth the global interceptor and the inline feature handlers share, so asserting it here covers the
 * mapped messaging for every error code (FE-CORE4 / FE-SEARCH3).
 */
describe('mapErrorToUx (QA2: error-code -> user UX)', () => {
  const err = (code: string, message = ''): ParsedApiError => ({ code, message, requestId: 'rid-1' });

  it('402 INSUFFICIENT_TOKENS -> prompt to buy tokens', () => {
    const ux = mapErrorToUx(err('INSUFFICIENT_TOKENS'));
    expect(ux.action?.route).toBe('/tokens');
    expect(ux.severity).toBe('warning');
  });

  it('502 ADVISOR_UNAVAILABLE -> reassures that no token was charged', () => {
    const ux = mapErrorToUx(err('ADVISOR_UNAVAILABLE'));
    expect(ux.message.toLowerCase()).toContain('no token was charged');
  });

  it('429 RATE_LIMITED -> a "slow down / try again" message', () => {
    const ux = mapErrorToUx(err('RATE_LIMITED'));
    expect(ux.message.toLowerCase()).toContain('try again');
  });

  it('403 EMAIL_NOT_VERIFIED -> steers the user into the verify flow', () => {
    const ux = mapErrorToUx(err('EMAIL_NOT_VERIFIED'));
    expect(ux.action?.route).toBe('/verify');
  });

  it('carries the requestId through for support', () => {
    expect(mapErrorToUx(err('PAYMENT_ERROR')).requestId).toBe('rid-1');
  });

  it('an unknown code falls back to a generic message (never a raw backend string)', () => {
    const ux = mapErrorToUx(err('SOMETHING_NEW', 'internal stacktrace leak'));
    expect(ux.title).toBe('Something went wrong');
    expect(ux.message).not.toContain('stacktrace');
  });
});
