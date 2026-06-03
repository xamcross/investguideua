import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { AppComponent } from './app.component';
import { AuthService } from './core/auth/auth.service';
import { AuthResponse, UserProfile } from './core/auth/auth.models';
import { environment } from '../environments/environment';

/**
 * 005-search-ui-fixes US1 (no-flash, scenario 5): the shell nav must reflect the tri-state
 * `authStatus`. While the startup silent `/auth/refresh` is in flight the status is `unknown` and
 * the nav must render NEITHER the account menu NOR the guest "Sign in / Register" CTAs — so a
 * logged-in user is never momentarily shown the guest buttons during session restore. Once refresh
 * settles the nav flips reactively to the authenticated or guest layout.
 */
describe('AppComponent (US1: tri-state nav, no guest-CTA flash on restore)', () => {
  const base = environment.apiBaseUrl;
  let fixture: ComponentFixture<AppComponent>;
  let httpMock: HttpTestingController;

  const user: UserProfile = {
    userId: 'u1',
    email: 'u@example.com',
    emailVerified: true,
    tokenBalance: 5,
    roles: ['USER'],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AppComponent);
  });

  afterEach(() => httpMock.verify());

  /** All anchor hrefs currently rendered in the nav. RouterLink emits these as href attributes. */
  function hrefs(): string[] {
    const el = fixture.nativeElement as HTMLElement;
    return Array.from(el.querySelectorAll('a')).map((a) => a.getAttribute('href') ?? '');
  }

  function hasSignOut(): boolean {
    return !!(fixture.nativeElement as HTMLElement).querySelector('.ig-linkbtn');
  }

  it('renders a neutral nav (no account menu, no guest CTAs) while the startup refresh is pending', () => {
    fixture.detectChanges(); // ngOnInit -> lang.init() + auth.refresh() (now in flight)

    // The refresh request is outstanding, so authStatus === 'unknown'.
    const expected = httpMock.expectOne(`${base}/auth/refresh`);

    const links = hrefs();
    expect(links).not.toContain('/login'); // no guest "Sign in"
    expect(links).not.toContain('/register'); // no guest "Register"
    expect(links).not.toContain('/account'); // no account menu
    expect(hasSignOut()).toBeFalse();
    // Neutral chrome (brand + language toggle) is still present.
    expect((fixture.nativeElement as HTMLElement).querySelector('.ig-lang')).toBeTruthy();

    // Resolve as authenticated and confirm the nav flips to the account menu (never showed CTAs).
    expected.flush({ accessToken: 'access-1', expiresInMs: 900000, user } as AuthResponse);
    fixture.detectChanges();

    const after = hrefs();
    expect(after).toContain('/account');
    expect(after).not.toContain('/login');
    expect(hasSignOut()).toBeTrue();
    // US1-2: the authenticated nav surfaces the live token balance (user.tokenBalance === 5).
    const balance = (fixture.nativeElement as HTMLElement).querySelector('.ig-balance');
    expect(balance?.textContent).toContain('5');
  });

  it('shows guest CTAs only AFTER the startup refresh fails (resolved guest)', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne(`${base}/auth/refresh`);

    // Still pending -> neutral, not guest.
    expect(hrefs()).not.toContain('/login');

    // Refresh fails -> the shell's error handler clears the session -> status resolves to 'guest'.
    req.flush(null, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    const links = hrefs();
    expect(links).toContain('/login');
    expect(links).toContain('/register');
    expect(links).not.toContain('/account');
    expect(hasSignOut()).toBeFalse();
  });
});
