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
});
