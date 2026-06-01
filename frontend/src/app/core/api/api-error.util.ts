import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorEnvelope } from '../auth/auth.models';

export interface ParsedApiError {
  code: string;
  message: string;
  requestId?: string;
  details?: Record<string, string>;
}

/**
 * Extract the §5.3 error envelope from an HttpErrorResponse. Full central code->UX mapping is
 * FE-CORE4 (a later ticket); this lightweight reader lets the auth pages show contextual messages
 * (EMAIL_TAKEN, VALIDATION_ERROR, UNAUTHORIZED) in the meantime.
 */
export function parseApiError(err: unknown): ParsedApiError {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as ApiErrorEnvelope | undefined;
    if (body && body.error && typeof body.error.code === 'string') {
      return {
        code: body.error.code,
        message: body.error.message,
        requestId: body.error.requestId,
        details: body.error.details,
      };
    }
    if (err.status === 0) {
      return { code: 'NETWORK', message: 'Cannot reach the server. Check your connection.' };
    }
  }
  return { code: 'UNKNOWN', message: 'Something went wrong. Please try again.' };
}
