import { ChangeDetectionStrategy, Component, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from './core/auth/auth.service';
import { NotificationHostComponent } from './core/errors/notification-host.component';
import { FooterComponent } from './core/layout/footer.component';
import { LanguageService } from './core/i18n/language.service';
import { PluralPipe } from './core/i18n/plural.pipe';
import { SeoService } from './core/seo/seo.service';
import { StructuredDataService } from './core/seo/structured-data.service';

/**
 * Root shell + top navigation (ticket FE-CORE1). The nav reflects auth state and the live
 * `tokenBalance` via signals from {@link AuthService}.
 *
 * Because the access token is in-memory only (FE-CORE2), a full page reload loses it. On startup
 * we attempt a single silent `/auth/refresh`: if the HttpOnly refresh cookie is present and valid,
 * the session is transparently restored; otherwise the user stays logged out (no error shown).
 *
 * The nav branches on the tri-state `auth.authStatus()`: while the startup refresh is still
 * in flight the status is `unknown` and the action area renders neither the account menu nor the
 * guest CTAs, so a logged-in user never flashes "Sign in / Register" during restore (005 US1-5).
 * Once the refresh settles (success -> `applySession`, failure -> `clearSession`, both of which
 * mark the session resolved) the status flips to `authenticated` / `guest` reactively.
 */
@Component({
  selector: 'ig-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    NotificationHostComponent,
    FooterComponent,
    TranslateModule,
    PluralPipe,
  ],
  template: `
    <a class="ig-skip" href="#ig-main">{{ 'nav.skipToContent' | translate }}</a>

    <header class="ig-topbar">
      <div class="ig-topbar__inner">
        <a class="ig-brand" routerLink="/" (click)="menuOpen.set(false)">
          <span class="ig-brand__mark" aria-hidden="true"></span>
          InvestGuide<b class="ig-brand__ua">UA</b>
        </a>

        <nav class="ig-nav" id="ig-primary-nav" [class.is-open]="menuOpen()">
          <div class="ig-nav__actions">
          @if (auth.authStatus() === 'authenticated') {
            <a routerLink="/search" routerLinkActive="is-active" ariaCurrentWhenActive="page" (click)="menuOpen.set(false)">{{ 'nav.search' | translate }}</a>
            <a routerLink="/history" routerLinkActive="is-active" ariaCurrentWhenActive="page" (click)="menuOpen.set(false)">{{ 'nav.history' | translate }}</a>
            <a routerLink="/providers" routerLinkActive="is-active" ariaCurrentWhenActive="page" (click)="menuOpen.set(false)">{{ 'nav.providers' | translate }}</a>
            <a routerLink="/tokens" routerLinkActive="is-active" ariaCurrentWhenActive="page" class="ig-balance"
               [title]="'nav.balanceTitle' | translate" (click)="menuOpen.set(false)">
              {{ auth.tokenBalance() | igPlural: 'token' }}
            </a>
            <a routerLink="/account" routerLinkActive="is-active" ariaCurrentWhenActive="page" (click)="menuOpen.set(false)">{{ 'nav.account' | translate }}</a>
            <button type="button" class="ig-linkbtn" (click)="logout()">{{ 'nav.signOut' | translate }}</button>
          } @else if (auth.authStatus() === 'guest') {
            <a routerLink="/login" routerLinkActive="is-active" ariaCurrentWhenActive="page" (click)="menuOpen.set(false)">{{ 'nav.signIn' | translate }}</a>
            <a routerLink="/register" routerLinkActive="is-active" ariaCurrentWhenActive="page" class="ig-btn ig-btn--nav" (click)="menuOpen.set(false)">{{ 'nav.register' | translate }}</a>
          }
          <!-- authStatus 'unknown' (startup refresh in flight): render neither menu nor guest CTAs
               so a logged-in user never flashes the Sign in / Register buttons (US1-5). -->
          </div>
        </nav>

        <div class="ig-topbar__tools">
          <button type="button" class="ig-lang" (click)="lang.toggle()"
                  [attr.aria-pressed]="lang.current() === 'en'"
                  [attr.aria-label]="(lang.current() === 'uk' ? 'lang.toEnglish' : 'lang.toUkrainian') | translate">
            <span lang="uk" [class.is-active]="lang.current() === 'uk'">UA</span><span aria-hidden="true">/</span><span lang="en" [class.is-active]="lang.current() === 'en'">EN</span>
          </button>

          <button type="button" class="ig-nav__toggle" (click)="menuOpen.set(!menuOpen())"
                  aria-controls="ig-primary-nav" [attr.aria-expanded]="menuOpen()"
                  [attr.aria-label]="(menuOpen() ? 'nav.menuClose' : 'nav.menuOpen') | translate">
            <span class="ig-burger" aria-hidden="true"></span>
          </button>
        </div>
      </div>
      <div class="ig-flag" aria-hidden="true"></div>
    </header>

    @if (menuOpen()) {
      <button type="button" class="ig-nav__scrim" tabindex="-1" aria-hidden="true"
              (click)="menuOpen.set(false)"></button>
    }

    <main id="ig-main" class="ig-container" tabindex="-1">
      <router-outlet />
    </main>

    <ig-footer />

    <p class="ig-sr-only" aria-live="polite">{{ lang.announcement() }}</p>

    <ig-notification-host />
  `,
  styles: [
    `
      /* Skip-to-content: hidden until focused, then pinned top-left above the sticky bar (a11y). */
      .ig-skip {
        position: fixed; top: .5rem; left: .5rem; z-index: 200;
        transform: translateY(-150%); transition: transform .2s var(--ease);
        background: var(--blue-600); color: #fff; font-weight: 700;
        padding: .6rem 1rem; border-radius: var(--radius-sm); box-shadow: var(--shadow-md);
      }
      .ig-skip:focus-visible { transform: translateY(0); text-decoration: none; }
      .ig-topbar {
        position: sticky; top: 0; z-index: 100;
        background: rgba(246, 243, 236, .82);
        -webkit-backdrop-filter: saturate(140%) blur(12px);
        backdrop-filter: saturate(140%) blur(12px);
        border-bottom: 1px solid var(--line);
      }
      .ig-topbar__inner {
        max-width: var(--maxw); margin: 0 auto; padding: .7rem 1rem;
        display: flex; align-items: center; gap: 1rem;
      }
      .ig-brand {
        font-family: var(--font-display); font-weight: 800; font-size: 1.25rem;
        text-decoration: none; color: var(--ink); display: inline-flex; align-items: center; gap: .55rem;
      }
      .ig-brand:hover { text-decoration: none; }
      .ig-brand__ua { color: var(--blue-600); font-style: normal; }
      .ig-brand__mark {
        width: 30px; height: 30px; border-radius: 9px; transform: rotate(-6deg);
        background: linear-gradient(180deg, var(--blue-600) 0 50%, var(--gold-500) 50% 100%);
        box-shadow: inset 0 0 0 1px rgba(255,255,255,.25), var(--shadow-sm);
      }
      .ig-nav { flex: 1; display: flex; justify-content: flex-end; }
      .ig-nav__actions { display: flex; align-items: center; gap: 1.1rem; }
      /* Base nav-link styling targets plain text links only; the balance pill and the
         register CTA keep their own padding/look (excluded so the more-specific .ig-nav a
         rule does not strip their horizontal padding or add the underline). */
      .ig-nav a:not(.ig-balance):not(.ig-btn--nav) {
        text-decoration: none; color: var(--muted); font-weight: 600; font-size: .92rem;
        position: relative; padding: .2rem 0; white-space: nowrap;
      }
      .ig-nav a:not(.ig-balance):not(.ig-btn--nav)::after { content: ""; position: absolute; left: 0; right: 100%; bottom: -2px; height: 2px;
        background: var(--gold-500); transition: right .25s var(--ease); }
      .ig-nav a:not(.ig-balance):not(.ig-btn--nav):hover { color: var(--ink); text-decoration: none; }
      .ig-nav a:not(.ig-balance):not(.ig-btn--nav):hover::after,
      .ig-nav a:not(.ig-balance):not(.ig-btn--nav).is-active::after { right: 0; }
      .ig-nav a:not(.ig-balance):not(.ig-btn--nav).is-active { color: var(--ink); }
      .ig-balance {
        font-family: var(--font-mono); font-weight: 700; font-size: .82rem; color: var(--navy-900) !important;
        background: linear-gradient(180deg, var(--gold-100), var(--gold-300));
        padding: .3rem .7rem .3rem .65rem; border-radius: 999px; display: inline-flex; align-items: center; gap: .4rem;
      }
      .ig-balance::after { display: none; }
      .ig-balance::before { content: ""; width: 7px; height: 7px; border-radius: 50%; background: var(--gold-600); flex: none; }
      .ig-btn--nav { color: #fff !important; padding: .4rem 1rem; }
      .ig-btn--nav::after { display: none; }
      .ig-linkbtn { background: none; border: none; color: var(--muted); font: inherit; font-weight: 600; cursor: pointer; white-space: nowrap; }
      .ig-linkbtn:hover { color: var(--danger-fg); }
      .ig-topbar__tools { display: flex; align-items: center; gap: .6rem; flex: none; }
      .ig-lang {
        display: inline-flex; align-items: center; justify-content: center; gap: .15rem;
        min-height: 40px; padding: .35rem .7rem; border: 1px solid var(--line-2);
        border-radius: 999px; background: var(--surface); font-family: var(--font-mono); font-size: .78rem;
        font-weight: 700; color: var(--muted); cursor: pointer;
      }
      .ig-lang span.is-active { color: var(--blue-600); }
      .ig-nav__toggle {
        display: none; width: 44px; height: 44px; align-items: center; justify-content: center;
        border: 1px solid var(--line-2); border-radius: var(--radius-sm); background: var(--surface); cursor: pointer;
      }
      .ig-burger, .ig-burger::before, .ig-burger::after {
        content: ""; display: block; width: 18px; height: 2px; background: var(--ink); border-radius: 2px; position: relative;
      }
      .ig-burger::before { position: absolute; top: -6px; }
      .ig-burger::after { position: absolute; top: 6px; }
      .ig-flag { height: 5px; background: linear-gradient(90deg, var(--blue-600) 0 50%, var(--gold-500) 50% 100%); }

      /* Dismiss scrim behind the open mobile drawer. Hidden on desktop (drawer never opens there). */
      .ig-nav__scrim { display: none; }

      @media (max-width: 760px) {
        .ig-nav__toggle { display: inline-flex; }
        .ig-nav__scrim {
          display: block; position: fixed; inset: 0; z-index: 90;
          border: 0; padding: 0; cursor: pointer;
          background: rgba(8, 20, 39, .38);
          -webkit-backdrop-filter: blur(1px); backdrop-filter: blur(1px);
          animation: ig-scrim-in .2s var(--ease);
        }
        @keyframes ig-scrim-in { from { opacity: 0; } to { opacity: 1; } }
        .ig-nav {
          position: absolute; left: 0; right: 0; top: 100%; flex: none; justify-content: stretch;
          background: var(--surface); border-bottom: 1px solid var(--line); box-shadow: var(--shadow-md);
          max-height: 0; overflow: hidden; transition: max-height .3s var(--ease);
        }
        .ig-nav.is-open { max-height: 80vh; }
        .ig-topbar__inner { position: relative; flex-wrap: wrap; }
        .ig-brand { flex: 1; }
        .ig-nav__actions { flex-direction: column; align-items: stretch; gap: 0; width: 100%; padding: .5rem 0; }
        .ig-nav__actions > * { padding: .85rem 1.25rem; }
        .ig-nav a::after { display: none; }
        .ig-balance { align-self: flex-start; }
        .ig-linkbtn { text-align: left; }
      }
      @media (prefers-reduced-motion: reduce) {
        .ig-nav { transition: none; }
      }
    `,
  ],
})
export class AppComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly lang = inject(LanguageService);
  private readonly router = inject(Router);
  private readonly seo = inject(SeoService);
  private readonly structuredData = inject(StructuredDataService);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  /** Responsive mobile-menu disclosure state (defect fix: nav + balance reachable on narrow screens). */
  protected readonly menuOpen = signal(false);

  ngOnInit(): void {
    // Apply the resolved startup language (localStorage -> Ukrainian default) and wire ngx-translate.
    // Runs on the server too (with the filesystem TranslateLoader) so prerendered HTML is localized.
    this.lang.init();

    // Wire per-page SEO head management (description, canonical, hreflang, robots, OG/Twitter).
    // Runs on the server platform too so the prerendered HTML carries these tags for crawlers.
    this.seo.init();

    // Site-wide structured data (Organization + WebSite + SearchAction) on every page.
    this.structuredData.setBase(this.lang.current());

    // Silent session restore is BROWSER-ONLY: during build-time prerender there is no backend and
    // no refresh cookie, so firing this on the server platform would issue a doomed HTTP call and
    // stall prerendering. The HttpOnly refresh cookie only exists in the browser anyway.
    if (this.isBrowser) {
      this.auth.refresh().subscribe({
        next: () => undefined,
        error: () => this.auth.clearSession(),
      });
    }
  }

  logout(): void {
    this.menuOpen.set(false);
    this.auth.logout();
    void this.router.navigate(['/']);
  }
}
