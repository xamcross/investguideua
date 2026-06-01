import {
  HttpContext,
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { parseApiError } from '../api/api-error.util';
import { mapErrorToUx } from './error-ux.util';
import { NotificationService } from './notification.service';

/**
 * When `true` on a request's HttpContext, the global error interceptor will NOT raise a toast for
 * that request — used by flows that render their own contextual inline error UI (e.g. the search
 * page's "buy tokens"/"verify" links, form field errors) so the user doesn't get a duplicate toast.
 * The error still propagates to the caller.
 */
export const SKIP_GLOBAL_ERROR = new HttpContextToken<boolean>(() => false);

/** Convenience for services/components: `this.http.get(url, { context: skipGlobalError() })`. */
export function skipGlobalError(context: HttpContext = new HttpContext()): HttpContext {
  return context.set(SKIP_GLOBAL_ERROR, true);
}

/** Auth endpoints surface their own inline messages on their pages; never toast for them. */
const AUTH_EXEMPT = ['/auth/login', '/auth/register', '/auth/verify', '/auth/refresh'];

function isApiUrl(url: string): boolean {
  return url.startsWith(environment.apiBaseUrl) || url.includes(environment.apiBaseUrl + '/');
}

/**
 * Central HTTP error handler (ticket FE-CORE4). Reads the §5.3 envelope and raises a mapped,
 * user-facing toast (via {@link mapErrorToUx}) so a raw error never reaches the user.
 *
 * Deliberate exclusions (handled elsewhere, to avoid double messaging):
 *  - `401 UNAUTHORIZED`: the auth interceptor transparently refreshes/replays or routes to /login.
 *  - `VALIDATION_ERROR`: forms render field-level errors inline.
 *  - auth endpoints: their pages show contextual inline messages.
 *  - requests flagged `SKIP_GLOBAL_ERROR`: the caller renders its own contextual UI.
 *
 * It must run as the OUTERMOST interceptor (registered before the auth interceptor) so that errors
 * the auth interceptor recovers from (a refreshed 401) never reach it. It always rethrows so
 * component-level handlers continue to work.
 */
export const globalErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((err: unknown) => {
      const skip = req.context.get(SKIP_GLOBAL_ERROR);
      const isApi = isApiUrl(req.url);
      const isAuthEndpoint = AUTH_EXEMPT.some((p) => req.url.includes(p));
      const status = err instanceof HttpErrorResponse ? err.status : -1;

      const handled = isApi && !skip && !isAuthEndpoint && status !== 401;
      if (handled) {
        const parsed = parseApiError(err);
        // VALIDATION_ERROR is rendered inline by forms; don't also toast it.
        if (parsed.code !== 'VALIDATION_ERROR') {
          notifications.pushError(mapErrorToUx(parsed));
        }
      }
      return throwError(() => err);
    }),
  );
};
