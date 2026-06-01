import { mapErrorToUx } from './error-ux.util';
import { ParsedApiError } from '../api/api-error.util';

/**
 * QA2 critical flow: 402 / 502 / 429 (and friends) UX. {@link mapErrorToUx} is the single source of
 * truth the global interceptor and the inline feature handlers share, so asserting it here covers the
 * mapped messaging for every error code (FE-CORE4 / FE-SEARCH3).
 *
 * Since i18n, the mapper returns translation KEYS (resolved by the toast host in the active language)
 * rather than literal text, so the assertions target keys/routes/severity and the raw-message
 * passthrough rather than English copy.
 */
describe('mapErrorToUx (QA2: error-code -> user UX)', () => {
  const err = (code: string, message = ''): ParsedApiError => ({ code, message, requestId: 'rid-1' });

  it('402 INSUFFICIENT_TOKENS -> prompt to buy tokens', () => {
    const ux = mapErrorToUx(err('INSUFFICIENT_TOKENS'));
    expect(ux.action?.route).toBe('/tokens');
    expect(ux.action?.label).toBe('error.insufficientTokens.action');
    expect(ux.severity).toBe('warning');
  });

  it('502 ADVISOR_UNAVAILABLE -> maps to the advisor-unavailable message key', () => {
    const ux = mapErrorToUx(err('ADVISOR_UNAVAILABLE'));
    expect(ux.message).toBe('error.advisorUnavailable.message');
  });

  it('429 RATE_LIMITED -> maps to the rate-limited message key', () => {
    const ux = mapErrorToUx(err('RATE_LIMITED'));
    expect(ux.message).toBe('error.rateLimited.message');
  });

  it('403 EMAIL_NOT_VERIFIED -> steers the user into the verify flow', () => {
    const ux = mapErrorToUx(err('EMAIL_NOT_VERIFIED'));
    expect(ux.action?.route).toBe('/verify');
  });

  it('VALIDATION_ERROR -> passes the server field message through verbatim', () => {
    const ux = mapErrorToUx(err('VALIDATION_ERROR', 'amount must be greater than 0'));
    expect(ux.messageText).toBe('amount must be greater than 0');
  });

  it('carries the requestId through for support', () => {
    expect(mapErrorToUx(err('PAYMENT_ERROR')).requestId).toBe('rid-1');
  });

  it('an unknown code falls back to a generic key (never leaks a raw backend string)', () => {
    const ux = mapErrorToUx(err('SOMETHING_NEW', 'internal stacktrace leak'));
    expect(ux.title).toBe('error.default.title');
    expect(ux.message).toBe('error.default.message');
    expect(ux.messageText).toBeUndefined();
  });
});
