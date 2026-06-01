import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { NotificationHostComponent } from './core/errors/notification-host.component';

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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationHostComponent],
  template: `
    <header class="ig-topbar">
      <div class="ig-topbar__inner">
        <a class="ig-brand" routerLink="/">
          <span class="ig-brand__mark" aria-hidden="true"></span>
          InvestGuide<span class="ig-brand__ua">UA</span>
        </a>

        <nav class="ig-nav">
          @if (auth.isAuthenticated()) {
            <a routerLink="/search" routerLinkActive="is-active">Search</a>
            <a routerLink="/history" routerLinkActive="is-active">History</a>
            <a routerLink="/providers" routerLinkActive="is-active">Providers</a>
            <a routerLink="/tokens" routerLinkActive="is-active" class="ig-balance" title="Token balance">
              {{ auth.tokenBalance() }} tokens
            </a>
            <a routerLink="/account" routerLinkActive="is-active">Account</a>
            <button type="button" class="ig-linkbtn" (click)="logout()">Sign out</button>
          } @else {
            <a routerLink="/login" routerLinkActive="is-active">Sign in</a>
            <a routerLink="/register" routerLinkActive="is-active" class="ig-btn ig-btn--nav">Register</a>
          }
        </nav>
      </div>
      <div class="ig-flag" aria-hidden="true"></div>
    </header>

    <main class="ig-container">
      <router-outlet />
    </main>

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
      .ig-flag { height: 4px; background: linear-gradient(90deg, var(--ig-blue) 50%, var(--ig-yellow) 50%); }
    `,
  ],
})
export class AppComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
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
