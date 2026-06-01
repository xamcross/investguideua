import { ParsedApiError } from '../api/api-error.util';

export type ErrorSeverity = 'error' | 'warning' | 'info';

export interface ErrorAction {
  label: string;
  /** In-app route to navigate to when the action is taken. */
  route: string;
}

export interface ErrorUx {
  title: string;
  message: string;
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
 * inline (e.g. the search page). It never surfaces a raw backend message for the codes it knows;
 * `VALIDATION_ERROR` is the one exception — its server message carries the specific field problem and
 * is shown as-is (forms also render it inline).
 */
export function mapErrorToUx(parsed: ParsedApiError): ErrorUx {
  const requestId = parsed.requestId;
  switch (parsed.code) {
    case 'INSUFFICIENT_TOKENS':
      return {
        title: 'Out of tokens',
        message: "You don't have enough tokens to run that search.",
        severity: 'warning',
        action: { label: 'Buy tokens', route: '/tokens' },
        requestId,
      };
    case 'RATE_LIMITED':
      return {
        title: 'Slow down a moment',
        message: 'Too many requests just now. Please wait a little and try again.',
        severity: 'warning',
        requestId,
      };
    case 'EMAIL_NOT_VERIFIED':
      return {
        title: 'Verify your email',
        message: 'Verify your email address to use this feature. Check your inbox for the link.',
        severity: 'warning',
        action: { label: 'Verify email', route: '/verify' },
        requestId,
      };
    case 'ADVISOR_UNAVAILABLE':
      return {
        title: 'Advisor is busy',
        message: 'The advisor is unavailable right now and no token was charged. Please try again shortly.',
        severity: 'warning',
        requestId,
      };
    case 'VALIDATION_ERROR':
      return {
        title: 'Check your input',
        message: parsed.message || 'Some fields need attention. Please review and try again.',
        severity: 'warning',
        requestId,
      };
    case 'EMAIL_TAKEN':
      return {
        title: 'Email already registered',
        message: 'That email is already registered. Try signing in instead.',
        severity: 'warning',
        action: { label: 'Sign in', route: '/login' },
        requestId,
      };
    case 'UNAUTHORIZED':
      return {
        title: 'Please sign in',
        message: 'Your session has expired. Please sign in again.',
        severity: 'warning',
        action: { label: 'Sign in', route: '/login' },
        requestId,
      };
    case 'FORBIDDEN':
      return {
        title: 'Not allowed',
        message: "You don't have permission to do that.",
        severity: 'error',
        requestId,
      };
    case 'NOT_FOUND':
      return {
        title: 'Not found',
        message: "We couldn't find what you were looking for.",
        severity: 'info',
        requestId,
      };
    case 'PAYMENT_ERROR':
      return {
        title: 'Payment problem',
        message: parsed.message || 'We could not start that payment. Please try again.',
        severity: 'error',
        requestId,
      };
    case 'NETWORK':
      return {
        title: 'Connection problem',
        message: 'Cannot reach the server. Check your connection and try again.',
        severity: 'error',
      };
    default:
      return {
        title: 'Something went wrong',
        message: 'An unexpected error occurred. Please try again.',
        severity: 'error',
        requestId,
      };
  }
}
