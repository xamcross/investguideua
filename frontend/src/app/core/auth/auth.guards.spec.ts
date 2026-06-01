import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { firstValueFrom, isObservable, Observable } from 'rxjs';
import { authGuard, verifiedGuard } from './auth.guards';
import { AuthService } from './auth.service';
import { AuthResponse, UserProfile } from './auth.models';
import { environment } from '../../../environments/environment';

/**
 * QA2 critical flow: route guards redirect unauthenticated / unverified users (FE-CORE3). The guards
 * are CanActivateFn closures, so each is executed inside the TestBed injection context; the backend
 * (refresh + /me) is mocked via HttpTestingController.
 */
describe('Route guards (QA2: auth + verified-email redirects)', () => {
  const base = environment.apiBaseUrl;
  let auth: AuthService;
  let httpMock: HttpTestingController;

  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

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
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /** Run a guard inside the injection context and normalise its result to a Promise. */
  function run(guard: typeof authGuard): Promise<boolean | UrlTree> {
    const result = TestBed.runInInjectionContext(() => guard(route, state));
    return isObservable(result)
      ? firstValueFrom(result as Observable<boolean | UrlTree>)
      : Promise.resolve(result as boolean | UrlTree);
  }

  function establishSession(overrides: Partial<UserProfile> = {}): void {
    auth.login('u@example.com', 'pw').subscribe();
    httpMock
      .expectOne(`${base}/auth/login`)
      .flush({ accessToken: 'a1', expiresInMs: 900000, user: user(overrides) } as AuthResponse);
  }

  it('authGuard: unauthenticated with a failing refresh redirects to /login', async () => {
    const result = run(authGuard);
    // No in-memory token on a hard load -> the guard attempts a silent refresh first.
    httpMock.expectOne(`${base}/auth/refresh`).flush(
      { error: { code: 'UNAUTHORIZED', message: 'no session', requestId: 'r1' } },
      { status: 401, statusText: 'Unauthorized' },
    );
    const redirect = await result;
    expect(redirect instanceof UrlTree).toBeTrue();
    expect((redirect as UrlTree).toString()).toBe('/login');
  });

  it('authGuard: an authenticated user is allowed through', async () => {
    establishSession();
    await expectAsync(run(authGuard)).toBeResolvedTo(true);
  });

  it('authGuard: unauthenticated with a SUCCESSFUL silent refresh is allowed through', async () => {
    const result = run(authGuard);
    // No in-memory token -> the guard restores the session via /auth/refresh (the cookie path).
    httpMock
      .expectOne(`${base}/auth/refresh`)
      .flush({ accessToken: 'a-refreshed', expiresInMs: 900000, user: user() } as AuthResponse);
    await expectAsync(result).toBeResolvedTo(true);
  });

  it('verifiedGuard: an authenticated but UNVERIFIED user is redirected to /verify', async () => {
    establishSession({ emailVerified: false });

    const result = run(verifiedGuard);
    // The guard must re-check via a fresh /me rather than trusting the cached flag (FE-CORE3).
    httpMock.expectOne(`${base}/me`).flush(user({ emailVerified: false }));

    const redirect = await result;
    expect(redirect instanceof UrlTree).toBeTrue();
    expect((redirect as UrlTree).toString()).toBe('/verify');
  });

  it('verifiedGuard: an authenticated and verified user is allowed through', async () => {
    establishSession({ emailVerified: true });

    const result = run(verifiedGuard);
    httpMock.expectOne(`${base}/me`).flush(user({ emailVerified: true, tokenBalance: 5 }));

    await expectAsync(result).toBeResolvedTo(true);
  });
});
