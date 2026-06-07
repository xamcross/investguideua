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
import { AnalyticsService } from './core/seo/analytics.service';
import { RouteFocusService } from './core/a11y/route-focus.service';

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
    <a class="ig-skip" href="#main-content">{{ 'a11y.skipToContent' | translate }}</a>

    <header class="ig-topbar">
      <div class="ig-topbar__inner">
        <a class="ig-brand" routerLink="/" (click)="menuOpen.set(false)">
          <span class="ig-brand__mark" aria-hidden="true"></span>
          InvestGuide<b class="ig-brand__ua">UA</b>
        </a>

        <nav class="ig-nav" id="ig-primary-nav" [class.is-open]="menuOpen()">
          <div class="ig-nav__actions">
          @if (auth.authStatus() === 'authenticated') {
            <!-- Token store sits left-most (first) so the balance is the primary nav anchor. -->
            <a routerLink="/tokens" routerLinkActive="is-active" ariaCurrentWhenActive="page" class="ig-balance"
               [title]="'nav.balanceTitle' | translate" (click)="menuOpen.set(false)">
              {{ auth.tokenBalance() | igPlural: 'token' }}
            </a>
            <a routerLink="/search" routerLinkActive="is-active" ariaCurrentWhenActive="page" (click)="menuOpen.set(false)">{{ 'nav.search' | translate }}</a>
            <a routerLink="/history" routerLinkActive="is-active" ariaCurrentWhenActive="page" (click)="menuOpen.set(false)">{{ 'nav.history' | translate }}</a>
            <!-- Providers is ADMIN-only (008-providers-admin-only): shown only to admins. The route
                 is also guarded (adminGuard) and the API enforces ADMIN, so this is UX, not the gate. -->
            @if (auth.isAdmin()) {
              <a routerLink="/providers" routerLinkActive="is-active" ariaCurrentWhenActive="page" (click)="menuOpen.set(false)">{{ 'nav.providers' | translate }}</a>
            }
            <a routerLink="/account" routerLinkActive="is-active" ariaCurrentWhenActive="page" (click)="menuOpen.set(false)">{{ 'nav.account' | translate }}</a>
            <!-- Sign out shows only inside the mobile drawer; on desktop it lives on the Account page. -->
            <button type="button" class="ig-linkbtn ig-nav__signout" (click)="logout()">{{ 'nav.signOut' | translate }}</button>
          } @else if (auth.authStatus() === 'guest') {
            <a routerLink="/login" routerLinkActive="is-active" ariaCurrentWhenActive="page" class="ig-btn ig-btn--ghost ig-nav__signin" (click)="menuOpen.set(false)">{{ 'nav.signIn' | translate }}</a>
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

    <main class="ig-container" id="main-content" tabindex="-1">
      <router-outlet />
    </main>

    <ig-footer />

    <p class="ig-sr-only" aria-live="polite">{{ lang.announcement() }}</p>

    <ig-notification-host />
  `,
  styles: [
    `
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
      .ig-nav a:not(.ig-balance):not(.ig-btn) {
        text-decoration: none; color: var(--muted); font-weight: 600; font-size: .92rem;
        position: relative; padding: .2rem 0; white-space: nowrap;
      }
      .ig-nav a:not(.ig-balance):not(.ig-btn)::after { content: ""; position: absolute; left: 0; right: 100%; bottom: -2px; height: 2px;
        background: var(--gold-500); transition: right .25s var(--ease); }
      .ig-nav a:not(.ig-balance):not(.ig-btn):hover { color: var(--ink); text-decoration: none; }
      .ig-nav a:not(.ig-balance):not(.ig-btn):hover::after,
      .ig-nav a:not(.ig-balance):not(.ig-btn).is-active::after { right: 0; }
      .ig-nav a:not(.ig-balance):not(.ig-btn).is-active { color: var(--ink); }
      .ig-balance {
        font-family: var(--font-mono); font-weight: 700; font-size: .82rem; color: var(--navy-900) !important;
        background: linear-gradient(180deg, var(--gold-100), var(--gold-300));
        padding: .3rem .7rem .3rem .65rem; border-radius: 999px; display: inline-flex; align-items: center; gap: .4rem;
      }
      .ig-balance::after { display: none; }
      .ig-balance::before { content: ""; width: 7px; height: 7px; border-radius: 50%; background: var(--gold-600); flex: none; }
      /* Nav buttons (Register primary + Sign in ghost) share a compact, shadowless sizing so they
         read as a matched pair in the bar; they grow to full-width touch targets in the drawer. */
      .ig-nav .ig-btn { min-height: 0; padding: .45rem 1.05rem; font-size: .9rem; box-shadow: none; }
      .ig-nav .ig-btn:hover:not(:disabled) { transform: none; box-shadow: none; }
      .ig-btn--nav { color: #fff !important; }
      .ig-btn--nav::after { display: none; }
      .ig-linkbtn { background: none; border: none; color: var(--muted); font: inherit; font-weight: 600; cursor: pointer; white-space: nowrap; }
      .ig-linkbtn:hover { color: var(--danger-fg); }
      /* Sign out is a drawer-only action on small screens; hidden in the desktop bar. */
      .ig-nav__signout { display: none; }
      .ig-topbar__tools { display: flex; align-items: center; gap: .6rem; flex: none; }
      .ig-lang {
        display: inline-flex; align-items: center; justify-content: center; gap: .15rem;
        min-height: var(--touch-min); padding: .35rem .7rem; border: 1px solid var(--line-2);
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
          max-height: 0; overflow: hidden;
          /* visibility:hidden removes the collapsed links from the tab order and from assistive
             tech (feature 007, FR-006). It flips after the collapse animation finishes, and back
             to visible immediately on open so the slide-down still plays. */
          visibility: hidden;
          transition: max-height .3s var(--ease), visibility 0s linear .3s;
        }
        .ig-nav.is-open { max-height: 85vh; visibility: visible; transition: max-height .3s var(--ease), visibility 0s; }
        .ig-topbar__inner { position: relative; flex-wrap: wrap; }
        .ig-brand { flex: 1; }
        .ig-nav__actions { flex-direction: column; align-items: stretch; gap: .3rem; width: 100%; padding: .55rem .7rem 1rem; }

        /* Every drawer row shares one geometry so the items line up and read as tappable controls,
           and each slides in (fade + translate) with a stagger when the drawer opens. */
        .ig-nav__actions > * {
          display: flex; align-items: center; box-sizing: border-box; width: 100%;
          min-height: var(--touch-min); padding: .7rem .85rem; margin: 0;
          border-radius: var(--radius-sm); font-size: 1rem; text-align: left;
          opacity: 0; transform: translateX(-10px);
        }
        .ig-nav.is-open .ig-nav__actions > * {
          opacity: 1; transform: none;
          transition: opacity .3s var(--ease), transform .3s var(--ease),
                      background .15s var(--ease), color .15s var(--ease);
        }
        .ig-nav.is-open .ig-nav__actions > *:nth-child(1) { transition-delay: .04s; }
        .ig-nav.is-open .ig-nav__actions > *:nth-child(2) { transition-delay: .08s; }
        .ig-nav.is-open .ig-nav__actions > *:nth-child(3) { transition-delay: .12s; }
        .ig-nav.is-open .ig-nav__actions > *:nth-child(4) { transition-delay: .16s; }
        .ig-nav.is-open .ig-nav__actions > *:nth-child(5) { transition-delay: .20s; }
        .ig-nav.is-open .ig-nav__actions > *:nth-child(6) { transition-delay: .24s; }

        /* Plain links become full-row menu items with a hover surface and an active accent bar. */
        .ig-nav a:not(.ig-balance):not(.ig-btn) {
          color: var(--ink); font-weight: 600; font-size: 1rem; padding: .7rem .85rem;
        }
        .ig-nav a:not(.ig-balance):not(.ig-btn):hover { color: var(--ink); background: var(--surface-2); }
        .ig-nav a:not(.ig-balance):not(.ig-btn).is-active {
          color: var(--blue-600); background: var(--surface-2); box-shadow: inset 3px 0 0 var(--gold-500);
        }
        .ig-nav a::after { display: none; }

        /* Token store: a full-width gold "wallet" row that anchors the top of the drawer. */
        .ig-balance { justify-content: flex-start; gap: .55rem; box-shadow: var(--shadow-sm); }

        /* Guest CTAs stack as full-width buttons: ghost Sign in paired above the primary Register. */
        .ig-nav__actions .ig-btn { justify-content: center; min-height: var(--touch-min); padding: .8rem 1rem; font-size: 1rem; }
        .ig-nav__signin { margin-top: .15rem; }

        /* Sign out: drawer-only, set apart with a divider and a danger affordance. */
        .ig-nav__signout {
          display: flex; justify-content: flex-start; align-items: center;
          margin-top: .35rem; padding-top: .9rem; border-top: 1px solid var(--line);
          border-radius: 0; color: var(--danger-fg); font-weight: 600;
        }
        .ig-nav__signout:hover { color: var(--danger-fg); background: var(--danger-bg); border-radius: var(--radius-sm); }
      }
      @media (prefers-reduced-motion: reduce) {
        .ig-nav { transition: none; }
        /* Never leave staggered rows stuck invisible if motion is reduced. */
        .ig-nav__actions > *,
        .ig-nav.is-open .ig-nav__actions > * { opacity: 1 !important; transform: none !important; transition: none !important; }
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
  private readonly analytics = inject(AnalyticsService);
  private readonly routeFocus = inject(RouteFocusService);
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

    // Cookieless Cloudflare Web Analytics beacon (010). Bakes into prerendered HTML; no-op when the
    // token is unset (local dev). No cookies => no consent banner.
    this.analytics.init();

    // Move focus to the main landmark after each SPA navigation (a11y; browser-only, FR-001).
    this.routeFocus.init();

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
