import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

/** Auth endpoints that must NOT carry a bearer header nor trigger a refresh-on-401 loop. */
const AUTH_EXEMPT = ['/auth/login', '/auth/register', '/auth/verify', '/auth/refresh'];

function isApiUrl(url: string): boolean {
  return url.startsWith(environment.apiBaseUrl) || url.includes(environment.apiBaseUrl + '/');
}

function isExempt(url: string): boolean {
  return AUTH_EXEMPT.some((p) => url.includes(p));
}

function withBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Functional HTTP interceptor (ticket FE-CORE2):
 *  - attaches `Authorization: Bearer <access>` to API requests (except auth endpoints);
 *  - on a 401, transparently runs a SINGLE shared `/auth/refresh` (single-flight) and replays
 *    the failed request with the new access token;
 *  - if the refresh itself fails, clears the session and routes to /login.
 *
 * Single-flight matters because BE-A4 rotates+revokes the refresh token on every refresh: if
 * three concurrent calls each fired their own refresh, two would present an already-rotated token
 * and be rejected. `AuthService.refresh()` returns one shared observable for all concurrent 401s.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  let request = req;
  const token = auth.accessToken;
  if (isApiUrl(req.url) && !isExempt(req.url) && token) {
    request = withBearer(req, token);
  }

  return next(request).pipe(
    catchError((err: unknown) => {
      const is401 = err instanceof HttpErrorResponse && err.status === 401;
      const refreshable = is401 && isApiUrl(req.url) && !isExempt(req.url);
      if (!refreshable) {
        return throwError(() => err);
      }

      // Attempt a (shared) refresh, then replay the original request with the new token.
      return auth.refresh().pipe(
        switchMap((newToken) => next(withBearer(req, newToken))),
        catchError((refreshErr: unknown) => {
          auth.clearSession();
          void router.navigate(['/login']);
          return throwError(() => refreshErr);
        }),
      );
    }),
  );
};
