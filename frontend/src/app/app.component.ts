import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from './core/auth/auth.service';
import { NotificationHostComponent } from './core/errors/notification-host.component';
import { LanguageService } from './core/i18n/language.service';
import { PluralPipe } from './core/i18n/plural.pipe';

/**
 * Root shell + top navigation (ticket FE-CORE1). The nav reflects auth state and the live
 * `tokenBalance` via signals from {@link AuthService}.
 *
 * Because the access token is in-memory only (FE-CORE2), a full page reload loses it. On startup
 * we attempt a single silent `/auth/refresh`: if the HttpOnly refresh cookie is present and valid,
 * the session is transparently restored; otherwise the user stays logged out (no error shown).
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
    TranslatePipe,
    PluralPipe,
  ],
  template: `
    <header class="ig-topbar">
      <div class="ig-topbar__inner">
        <a class="ig-brand" routerLink="/">
          <span class="ig-brand__mark" aria-hidden="true"></span>
          InvestGuide<span class="ig-brand__ua">UA</span>
        </a>

        <nav class="ig-nav">
          @if (auth.isAuthenticated()) {
            <a routerLink="/search" routerLinkActive="is-active">{{ 'nav.search' | translate }}</a>
            <a routerLink="/history" routerLinkActive="is-active">{{ 'nav.history' | translate }}</a>
            <a routerLink="/providers" routerLinkActive="is-active">{{ 'nav.providers' | translate }}</a>
            <a routerLink="/tokens" routerLinkActive="is-active" class="ig-balance"
               [title]="'nav.balanceTitle' | translate">
              {{ auth.tokenBalance() | igPlural: 'token' }}
            </a>
            <a routerLink="/account" routerLinkActive="is-active">{{ 'nav.account' | translate }}</a>
            <button type="button" class="ig-linkbtn" (click)="logout()">{{ 'nav.signOut' | translate }}</button>
          } @else {
            <a routerLink="/login" routerLinkActive="is-active">{{ 'nav.signIn' | translate }}</a>
            <a routerLink="/register" routerLinkActive="is-active" class="ig-btn ig-btn--nav">{{ 'nav.register' | translate }}</a>
          }

          <button type="button" class="ig-lang" (click)="lang.toggle()"
                  [attr.aria-label]="(lang.current() === 'uk' ? 'lang.toEnglish' : 'lang.toUkrainian') | translate">
            <span [class.is-active]="lang.current() === 'uk'">UA</span><span aria-hidden="true">/</span><span [class.is-active]="lang.current() === 'en'">EN</span>
          </button>
        </nav>
      </div>
      <div class="ig-flag" aria-hidden="true"></div>
    </header>

    <main class="ig-container">
      <router-outlet />
    </main>

    <p class="ig-sr-only" aria-live="polite">{{ lang.announcement() }}</p>

    <ig-notification-host />
  `,
  styles: [
    `
      .ig-topbar { background: var(--ig-surface); border-bottom: 1px solid var(--ig-border); }
      .ig-topbar__inner {
        max-width: 920px; margin: 0 auto; padding: 0.75rem 1rem;
        display: flex; align-items: center; justify-content: space-between; gap: 1rem;
      }
      .ig-brand {
        font-weight: 800; font-size: 1.2rem; text-decoration: none; color: var(--ig-ink);
        display: inline-flex; align-items: center; gap: 0.5rem;
      }
      .ig-brand__ua { color: var(--ig-blue); }
      .ig-brand__mark {
        width: 14px; height: 14px; border-radius: 50%;
        background: linear-gradient(180deg, var(--ig-blue) 50%, var(--ig-yellow) 50%);
      }
      .ig-nav { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
      .ig-nav a { text-decoration: none; color: var(--ig-muted); font-weight: 600; font-size: 0.92rem; }
      .ig-nav a.is-active { color: var(--ig-blue); }
      .ig-balance { background: rgba(0, 87, 183, 0.08); padding: 0.25rem 0.6rem; border-radius: 999px; color: var(--ig-blue) !important; }
      .ig-btn--nav { color: #fff !important; padding: 0.4rem 0.9rem; border-radius: 8px; }
      .ig-linkbtn { background: none; border: none; color: var(--ig-muted); font: inherit; font-weight: 600; cursor: pointer; }
      .ig-linkbtn:hover { color: var(--ig-danger); }
      .ig-nav a, .ig-nav button { white-space: nowrap; }
      .ig-lang {
        display: inline-flex; align-items: center; gap: 0.1rem; min-height: 32px;
        padding: 0.2rem 0.55rem; border: 1px solid var(--ig-border); border-radius: 999px;
        background: none; font: inherit; font-size: 0.82rem; font-weight: 700;
        color: var(--ig-muted); cursor: pointer;
      }
      .ig-lang span.is-active { color: var(--ig-blue); }
      .ig-lang:focus-visible { outline: 2px solid var(--ig-blue); outline-offset: 2px; }
      .ig-flag { height: 4px; background: linear-gradient(90deg, var(--ig-blue) 50%, var(--ig-yellow) 50%); }
      .ig-sr-only {
        position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
        overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0;
      }
    `,
  ],
})
export class AppComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly lang = inject(LanguageService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    // Apply the resolved startup language (localStorage -> Ukrainian default) and wire ngx-translate.
    this.lang.init();

    // Silent session restore. The interceptor exempts /auth/refresh, so a failure here just
    // resolves to "not logged in" without redirect noise.
    this.auth.refresh().subscribe({
      next: () => undefined,
      error: () => this.auth.clearSession(),
    });
  }

  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/']);
  }
}
