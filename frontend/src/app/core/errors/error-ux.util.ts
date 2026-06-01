import { ParsedApiError } from '../api/api-error.util';

export type ErrorSeverity = 'error' | 'warning' | 'info';

export interface ErrorAction {
  /** Translation KEY for the action label (resolved by the toast host). */
  label: string;
  /** In-app route to navigate to when the action is taken. */
  route: string;
}

export interface ErrorUx {
  /** Translation KEY for the title. */
  title: string;
  /** Translation KEY for the message. */
  message: string;
  /**
   * Raw, already-final message text from the server (e.g. a VALIDATION_ERROR field problem). When
   * present the toast host shows this verbatim instead of translating {@link message}. Backend error
   * text is intentionally out of i18n scope, so it is passed through as-is.
   */
  messageText?: string;
  severity: ErrorSeverity;
  /** Optional contextual action (e.g. "Buy tokens"). */
  action?: ErrorAction;
  /** Carried through for support; rendered copyable by the toast host. */
  requestId?: string;
}

/**
 * Central mapping from the §5.3 error envelope to user-facing UX (ticket FE-CORE4).
 *
 * This is the single source of truth for "what does error code X mean to the user", reused by the
 * global error interceptor (toast surface) and by feature components that render the same codes
 * inline (e.g. the search page). It returns translation KEYS (not literal text) so the toast host can
 * render them in the active language and re-translate live on a language switch. It never surfaces a
 * raw backend message for the codes it knows; `VALIDATION_ERROR` / `PAYMENT_ERROR` are the exceptions
 * — their server message carries the specific problem and is passed through via {@link ErrorUx.messageText}.
 */
export function mapErrorToUx(parsed: ParsedApiError): ErrorUx {
  const requestId = parsed.requestId;
  switch (parsed.code) {
    case 'INSUFFICIENT_TOKENS':
      return {
        title: 'error.insufficientTokens.title',
        message: 'error.insufficientTokens.message',
        severity: 'warning',
        action: { label: 'error.insufficientTokens.action', route: '/tokens' },
        requestId,
      };
    case 'RATE_LIMITED':
      return {
        title: 'error.rateLimited.title',
        message: 'error.rateLimited.message',
        severity: 'warning',
        requestId,
      };
    case 'EMAIL_NOT_VERIFIED':
      return {
        title: 'error.emailNotVerified.title',
        message: 'error.emailNotVerified.message',
        severity: 'warning',
        action: { label: 'error.emailNotVerified.action', route: '/verify' },
        requestId,
      };
    case 'ADVISOR_UNAVAILABLE':
      return {
        title: 'error.advisorUnavailable.title',
        message: 'error.advisorUnavailable.message',
        severity: 'warning',
        requestId,
      };
    case 'VALIDATION_ERROR':
      return {
        title: 'error.validation.title',
        message: 'error.validation.message',
        messageText: parsed.message || undefined,
        severity: 'warning',
        requestId,
      };
    case 'EMAIL_TAKEN':
      return {
        title: 'error.emailTaken.title',
        message: 'error.emailTaken.message',
        severity: 'warning',
        action: { label: 'error.emailTaken.action', route: '/login' },
        requestId,
      };
    case 'UNAUTHORIZED':
      return {
        title: 'error.unauthorized.title',
        message: 'error.unauthorized.message',
        severity: 'warning',
        action: { label: 'error.unauthorized.action', route: '/login' },
        requestId,
      };
    case 'FORBIDDEN':
      return {
        title: 'error.forbidden.title',
        message: 'error.forbidden.message',
        severity: 'error',
        requestId,
      };
    case 'NOT_FOUND':
      return {
        title: 'error.notFound.title',
        message: 'error.notFound.message',
        severity: 'info',
        requestId,
      };
    case 'PAYMENT_ERROR':
      return {
        title: 'error.payment.title',
        message: 'error.payment.message',
        messageText: parsed.message || undefined,
        severity: 'error',
        requestId,
      };
    case 'NETWORK':
      return {
        title: 'error.network.title',
        message: 'error.network.message',
        severity: 'error',
      };
    default:
      return {
        title: 'error.default.title',
        message: 'error.default.message',
        severity: 'error',
        requestId,
      };
  }
}
