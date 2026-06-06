import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { AuthService } from './auth.service';

/**
 * Route guards (ticket FE-CORE3, §4.1, §4.2, §5.3).
 *
 * These guards are best-effort UX only: the backend remains the source of truth (a deep-link or a
 * stale access token can still reach the API, where `401 UNAUTHORIZED` / `403 EMAIL_NOT_VERIFIED`
 * are enforced and surfaced by the global error handling — FE-CORE4 — and the search/buy flows).
 */

/**
 * Ensure the caller has a live session. The access token is in-memory only (FE-CORE2), so on a hard
 * page load it is gone until a silent `/auth/refresh` restores it from the HttpOnly refresh cookie.
 * If not currently authenticated we attempt that single (single-flight) refresh; success allows the
 * route, failure redirects to `/login`.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  return auth.refresh().pipe(
    map(() => true as const),
    catchError(() => of(router.parseUrl('/login'))),
  );
};

/**
 * Gate the token-spending areas (Search, Buy Tokens) on a VERIFIED email. Mirrors the backend
 * `EMAIL_NOT_VERIFIED`. Per FE-CORE3 the guard must NOT trust a possibly-stale cached `emailVerified`
 * flag (the user may have verified in another tab, or hold a token minted pre-verification): it
 * re-checks via a fresh `/me` call on entry.
 *
 *  - not authenticated (and refresh fails)  -> redirect to `/login`
 *  - authenticated but `/me` unreadable      -> redirect to `/login` (session no longer valid)
 *  - authenticated but not verified          -> redirect to `/verify`
 *  - authenticated and verified              -> allow
 */
export const verifiedGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const ensureSession: Observable<boolean> = auth.isAuthenticated()
    ? of(true)
    : auth.refresh().pipe(
        map(() => true),
        catchError(() => of(false)),
      );

  return ensureSession.pipe(
    switchMap((authed) => {
      if (!authed) {
        return of(router.parseUrl('/login'));
      }
      // Fresh re-check — do not rely on the cached flag.
      return auth.loadMe().pipe(
        map((user) => {
          if (!user) {
            return router.parseUrl('/login');
          }
          return user.emailVerified ? true : router.parseUrl('/verify');
        }),
      );
    }),
  );
};

/**
 * Gate ADMIN-only areas (Providers) on the ADMIN role (008-providers-admin-only, §FR-005).
 * Best-effort UX only — the backend returns 403 for a non-admin regardless, so a stale client role
 * cannot leak data. Ensures a live session first (mirrors `authGuard`'s single-flight refresh), then
 * re-checks the freshly loaded profile's role rather than a possibly-stale cached flag:
 *
 *  - not authenticated (and refresh fails)  -> redirect to `/login`
 *  - authenticated but `/me` unreadable      -> redirect to `/login`
 *  - authenticated but not ADMIN             -> redirect to `/account` (safe authenticated landing)
 *  - authenticated and ADMIN                 -> allow
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const ensureSession: Observable<boolean> = auth.isAuthenticated()
    ? of(true)
    : auth.refresh().pipe(
        map(() => true),
        catchError(() => of(false)),
      );

  return ensureSession.pipe(
    switchMap((authed) => {
      if (!authed) {
        return of(router.parseUrl('/login'));
      }
      // Re-fetch the profile so the role check reflects server truth, not a stale cached value.
      return auth.loadMe().pipe(
        map((user) => {
          if (!user) {
            return router.parseUrl('/login');
          }
          return (user.roles ?? []).includes('ADMIN') ? true : router.parseUrl('/account');
        }),
      );
    }),
  );
};
