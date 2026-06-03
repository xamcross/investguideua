import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, shareReplay, tap } from 'rxjs';
import { map, catchError, finalize } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { skipGlobalError } from '../errors/global-error.interceptor';
import {
  AuthResponse,
  RegisterResponse,
  UserProfile,
  VerifyResponse,
} from './auth.models';

/**
 * Central authentication state + flows (ticket FE-CORE2).
 *
 * Token storage model (security-critical, section 10 / AC #10):
 *  - The ACCESS token lives in memory only (this `#accessToken` field). It is never written to
 *    localStorage/sessionStorage, so XSS cannot exfiltrate a persisted token and a page reload
 *    simply re-derives a session via the refresh cookie.
 *  - The REFRESH token is held in an HttpOnly, Secure, SameSite cookie set by the backend. JS
 *    cannot read it; the browser attaches it automatically on `/auth/*` requests (withCredentials).
 *  - Tokens are never logged.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /**
   * In-memory access token (never persisted). Held in a SIGNAL so `isAuthenticated` and the shell
   * nav react to login/refresh/logout. A plain field froze `isAuthenticated` (a computed with no
   * signal dependency memoizes its first value), which let a logged-in user keep seeing the guest
   * nav (005-search-ui-fixes US1).
   */
  readonly #accessToken = signal<string | null>(null);

  /**
   * Whether the startup silent refresh has settled (success OR failure). Until it has, `authStatus`
   * is `unknown` so the shell can render a neutral nav and never flash guest CTAs at a logged-in
   * user during session restore (US1-5).
   */
  readonly #authResolved = signal(false);

  /** Reactive current-user state for the shell/nav and guards. */
  private readonly _user = signal<UserProfile | null>(null);
  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this.#accessToken() !== null);
  readonly tokenBalance = computed(() => this._user()?.tokenBalance ?? 0);
  readonly emailVerified = computed(() => this._user()?.emailVerified ?? false);

  /**
   * Tri-state session status for the shell nav: `unknown` before the startup refresh resolves,
   * then `authenticated` / `guest`. Derived from the access-token signal (the source of truth) so a
   * `loadMe()`-only profile without a token never reads as authenticated.
   */
  readonly authStatus = computed<'unknown' | 'authenticated' | 'guest'>(() =>
    !this.#authResolved() ? 'unknown' : this.#accessToken() !== null ? 'authenticated' : 'guest',
  );

  /** Single-flight refresh: while a refresh is in progress, all callers share this observable. */
  #refresh$: Observable<string> | null = null;

  get accessToken(): string | null {
    return this.#accessToken();
  }

  // ---- public flows -------------------------------------------------------------------

  register(email: string, password: string): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.base}/auth/register`, { email, password });
  }

  verify(token: string): Observable<VerifyResponse> {
    return this.http
      .post<VerifyResponse>(`${this.base}/auth/verify`, { token })
      .pipe(
        tap((res) => {
          // If the user is already in session, reflect the new verified state + balance live.
          const current = this._user();
          if (current) {
            this._user.set({
              ...current,
              emailVerified: res.emailVerified,
              tokenBalance: res.tokenBalance,
            });
          }
        }),
      );
  }

  login(email: string, password: string): Observable<UserProfile> {
    return this.http
      .post<AuthResponse>(
        `${this.base}/auth/login`,
        { email, password },
        { withCredentials: true }, // allow the Set-Cookie (refresh) to be stored
      )
      .pipe(
        tap((res) => this.applySession(res)),
        map((res) => res.user),
      );
  }

  /** Refresh the access token using the HttpOnly refresh cookie. De-duplicated (single-flight). */
  refresh(): Observable<string> {
    if (this.#refresh$) {
      return this.#refresh$;
    }
    this.#refresh$ = this.http
      .post<AuthResponse>(`${this.base}/auth/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((res) => this.applySession(res)),
        map((res) => res.accessToken),
        shareReplay(1),
        finalize(() => {
          this.#refresh$ = null;
        }),
      );
    return this.#refresh$;
  }

  /** Re-fetch the live profile (e.g. after verification in another tab, or in the verified guard).
   *  Opts out of the global error toast: callers (guards / account page) handle failures themselves. */
  loadMe(): Observable<UserProfile | null> {
    return this.http.get<UserProfile>(`${this.base}/me`, { context: skipGlobalError() }).pipe(
      tap((user) => this._user.set(user)),
      map((user) => user as UserProfile | null),
      catchError(() => of(null)),
    );
  }

  logout(): void {
    // Client-side logout: drop the in-memory access token + user state. A dedicated server-side
    // refresh-cookie revocation endpoint is a later ticket; until then the refresh cookie simply
    // expires by TTL. No tokens are persisted client-side, so nothing else to clear.
    this.clearSession();
  }

  /** Drop in-memory session state (called on refresh failure / logout). */
  clearSession(): void {
    this.#accessToken.set(null);
    this._user.set(null);
    // The session is now definitively resolved as "no session" -> nav can show guest CTAs.
    this.#authResolved.set(true);
  }

  /**
   * Reflect a server-authoritative token balance in the shell (e.g. after a search debits one, or a
   * payment credits some). The server is always the source of truth; the client only mirrors it.
   */
  updateTokenBalance(balance: number): void {
    const current = this._user();
    if (current) {
      this._user.set({ ...current, tokenBalance: balance });
    }
  }

  private applySession(res: AuthResponse): void {
    this.#accessToken.set(res.accessToken);
    this._user.set(res.user);
    // A live session is established -> resolved as authenticated.
    this.#authResolved.set(true);
  }
}
