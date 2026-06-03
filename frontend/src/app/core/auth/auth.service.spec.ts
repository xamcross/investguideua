import { computed } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { AuthResponse, UserProfile, VerifyResponse } from './auth.models';
import { environment } from '../../../environments/environment';

/**
 * QA2 critical flow: register -> verify -> balance = 5, plus the FE-CORE2 token-storage and
 * single-flight-refresh guarantees. HttpTestingController stands in for the backend (mocked backend
 * is acceptable per the QA2 DoD), so these assert the client behaviour without a live server.
 */
describe('AuthService (QA2: session, verification, single-flight refresh)', () => {
  const base = environment.apiBaseUrl;
  let service: AuthService;
  let httpMock: HttpTestingController;

  const user = (overrides: Partial<UserProfile> = {}): UserProfile => ({
    userId: 'u1',
    email: 'u@example.com',
    emailVerified: false,
    tokenBalance: 0,
    roles: ['USER'],
    ...overrides,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function login(): void {
    let result: UserProfile | undefined;
    service.login('u@example.com', 'pw').subscribe((u) => (result = u));
    const req = httpMock.expectOne(`${base}/auth/login`);
    expect(req.request.method).toBe('POST');
    // Refresh cookie support requires credentials on the login call (FE-CORE2 / BE-A4).
    expect(req.request.withCredentials).toBeTrue();
    const res: AuthResponse = { accessToken: 'access-1', expiresInMs: 900000, user: user() };
    req.flush(res);
    expect(result).toEqual(user());
  }

  it('keeps the access token in memory only (never in web storage) after login', () => {
    spyOn(Storage.prototype, 'setItem').and.callThrough();
    login();

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.accessToken).toBe('access-1');
    // AC #10 storage model: nothing about the token is written to local/session storage.
    expect(Storage.prototype.setItem).not.toHaveBeenCalled();
  });

  it('verification reflects emailVerified=true and the new balance of 5 in the live session', () => {
    login(); // establishes an in-session, as-yet-unverified user with balance 0

    let res: VerifyResponse | undefined;
    service.verify('verify-token').subscribe((r) => (res = r));
    const req = httpMock.expectOne(`${base}/auth/verify`);
    expect(req.request.method).toBe('POST');
    const verifyResponse: VerifyResponse = {
      emailVerified: true,
      tokenBalance: 5,
      firstVerification: true,
      message: 'Email verified. 5 free tokens added.',
    };
    req.flush(verifyResponse);

    expect(res).toEqual(verifyResponse);
    // The shell/nav state updates live (FE-AUTH3 DoD): verified + exactly 5 tokens.
    expect(service.emailVerified()).toBeTrue();
    expect(service.tokenBalance()).toBe(5);
  });

  it('de-duplicates concurrent refreshes into a SINGLE /auth/refresh (single-flight, FE-CORE2)', () => {
    const tokens: string[] = [];
    // Three concurrent callers (e.g. /me + /tokens/packs + a nav guard all racing a 401).
    service.refresh().subscribe((t) => tokens.push(t));
    service.refresh().subscribe((t) => tokens.push(t));
    service.refresh().subscribe((t) => tokens.push(t));

    // Exactly ONE network refresh is issued, not three (rotation would revoke the others -> spurious logout).
    const req = httpMock.expectOne(`${base}/auth/refresh`);
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ accessToken: 'access-2', expiresInMs: 900000, user: user() } as AuthResponse);

    expect(tokens).toEqual(['access-2', 'access-2', 'access-2']);
    expect(service.accessToken).toBe('access-2');
  });

  it('issues a fresh /auth/refresh after the previous one settles (single-flight resets)', () => {
    // First refresh in flight -> settles.
    service.refresh().subscribe();
    httpMock
      .expectOne(`${base}/auth/refresh`)
      .flush({ accessToken: 'access-2', expiresInMs: 900000, user: user() } as AuthResponse);

    // Because `finalize` clears `#refresh$`, a later caller must trigger a brand-new request
    // (not replay the settled one) — otherwise a rotated/revoked token would be reused.
    let second: string | undefined;
    service.refresh().subscribe((t) => (second = t));
    httpMock
      .expectOne(`${base}/auth/refresh`)
      .flush({ accessToken: 'access-3', expiresInMs: 900000, user: user() } as AuthResponse);

    expect(second).toBe('access-3');
    expect(service.accessToken).toBe('access-3');
  });

  it('clears the in-memory session on logout', () => {
    login();
    service.logout();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.accessToken).toBeNull();
    expect(service.user()).toBeNull();
  });

  // --- 005-search-ui-fixes US1: reactivity regression guards --------------------------------
  // The original defect: `isAuthenticated` was a computed() over a NON-signal field, so it
  // memoized its first value and never recomputed. The pre-existing tests read it only AFTER a
  // session was applied, so they passed even while the bug shipped. These tests read it FIRST
  // (while logged out), then mutate the session — the ordering that exposes the frozen computed.

  it('isAuthenticated updates reactively when first read BEFORE the session is applied', () => {
    // First read happens while logged out -> observe false (this is what froze the old computed).
    expect(service.isAuthenticated()).toBeFalse();
    // A session is then established on the SAME instance...
    login();
    // ...and the flag MUST now report true. The non-reactive computed stays frozen at false here.
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('isAuthenticated propagates through a downstream computed consumer (template-like read)', () => {
    // Mirrors the shell nav reading auth.isAuthenticated() during the initial render, before the
    // startup refresh resolves. The consumer must re-evaluate when the session changes.
    const navIsAuthed = computed(() => service.isAuthenticated());
    expect(navIsAuthed()).toBeFalse();
    login();
    expect(navIsAuthed()).toBeTrue();
  });

  it('authStatus is "unknown" until the startup refresh resolves, then "authenticated" then "guest"', () => {
    // Neutral nav state before the startup refresh settles (no guest-CTA flash, US1-5).
    expect(service.authStatus()).toBe('unknown');

    // A successful refresh establishes a session.
    service.refresh().subscribe();
    httpMock
      .expectOne(`${base}/auth/refresh`)
      .flush({ accessToken: 'access-2', expiresInMs: 900000, user: user() } as AuthResponse);
    expect(service.authStatus()).toBe('authenticated');

    // Logout drops to a resolved guest state.
    service.logout();
    expect(service.authStatus()).toBe('guest');
  });

  it('authStatus resolves to "guest" when the startup refresh fails (no session restored)', () => {
    expect(service.authStatus()).toBe('unknown');
    // Mirror the shell: on refresh error the app clears the session (which resolves to guest).
    service.refresh().subscribe({ next: () => undefined, error: () => service.clearSession() });
    httpMock
      .expectOne(`${base}/auth/refresh`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(service.authStatus()).toBe('guest');
  });
});
