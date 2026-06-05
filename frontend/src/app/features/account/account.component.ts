import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { PluralPipe } from '../../core/i18n/plural.pipe';
import { LoadingStateComponent } from '../shared/loading-state.component';
import { ErrorStateComponent } from '../shared/error-state.component';

/**
 * Account page (ticket FE-ACCT1, §5.1 `/me`, §10).
 *
 * Shows the live profile from `/me` (email, verification status, token balance, roles), a logout
 * action, and a data/account-deletion request entry point. For the MVP, deletion is a request action
 * (§10 PII minimization): there is no self-service delete endpoint yet, so we surface a clear
 * support-email request path rather than a fake button.
 */
@Component({
  selector: 'ig-account',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule, PluralPipe, LoadingStateComponent, ErrorStateComponent],
  template: `
    <section class="ig-card reveal d1">
      <div class="ig-page-head">
        <p class="ig-kicker">{{ 'account.eyebrow' | translate }}</p>
        <h1 class="ig-display">{{ 'account.title' | translate }}</h1>
      </div>

      @if (loading()) {
        <ig-loading-state [label]="'account.loading' | translate" />
      } @else if (user()) {
        @if (user(); as u) {
        <dl class="ig-profile">
          <dt>{{ 'account.email' | translate }}</dt>
          <dd>{{ u.email }}</dd>

          <dt>{{ 'account.emailStatus' | translate }}</dt>
          <dd>
            @if (u.emailVerified) {
              <span class="ig-badge ig-badge--ok">{{ 'account.verified' | translate }}</span>
            } @else {
              <span class="ig-badge ig-badge--warn">{{ 'account.notVerified' | translate }}</span>
              <a routerLink="/verify" class="ig-inline-link">{{ 'account.verifyNow' | translate }}</a>
            }
          </dd>

          <dt>{{ 'account.tokenBalance' | translate }}</dt>
          <dd>
            <span class="ig-acct__balance">{{ u.tokenBalance | igPlural: 'token' }}</span>
            <a routerLink="/tokens" class="ig-inline-link">{{ 'account.buyMore' | translate }}</a>
          </dd>

          <dt>{{ 'account.roles' | translate }}</dt>
          <dd>{{ u.roles.join(', ') }}</dd>
        </dl>

        <div class="ig-actions">
          <button type="button" class="ig-btn ig-btn--primary" (click)="logout()">{{ 'account.signOut' | translate }}</button>
        </div>

        <div class="ig-danger-zone">
          <h2>{{ 'account.deleteTitle' | translate }}</h2>
          <p class="ig-muted">{{ 'account.deleteBody' | translate }}</p>
          @if (!deletionRequested()) {
            <a class="ig-btn ig-btn--ghost" [href]="deletionMailto()">{{ 'account.requestDeletion' | translate }}</a>
            <button type="button" class="ig-linkbtn" (click)="deletionRequested.set(true)">
              {{ 'account.sentRequest' | translate }}
            </button>
          } @else {
            <div class="ig-alert ig-alert--info">{{ 'account.deletionThanks' | translate }}</div>
          }
        </div>
        }
      } @else {
        <ig-error-state [message]="'account.loadError' | translate">
          <a routerLink="/login">{{ 'account.signInAgain' | translate }}</a>
        </ig-error-state>
      }
    </section>
  `,
  styles: [
    `
      .ig-profile {
        display: grid; grid-template-columns: auto 1fr; gap: 0; margin: 0 0 1.75rem;
        align-items: stretch;
      }
      .ig-profile dt {
        font-family: var(--font-mono); font-size: .68rem; text-transform: uppercase; letter-spacing: .08em;
        color: var(--muted); padding: .75rem 1.25rem .75rem 0; border-top: 1px solid var(--line);
      }
      .ig-profile dd { margin: 0; padding: .75rem 0; border-top: 1px solid var(--line); }
      .ig-acct__balance { font-family: var(--font-mono); font-weight: 700; }
      .ig-inline-link { margin-left: 0.6rem; font-size: 0.85rem; }
      .ig-actions { margin-bottom: 2rem; }
      .ig-danger-zone {
        background: var(--surface-2); border-left: 4px solid var(--gold-500);
        border-radius: var(--radius-sm); padding: 1.25rem 1.25rem 1rem;
      }
      .ig-danger-zone h2 { font-size: 1.05rem; margin: 0 0 0.5rem; }
      .ig-danger-zone .ig-btn--ghost { margin-right: 0.75rem; }
      .ig-linkbtn { background: none; border: none; color: var(--muted); font: inherit; cursor: pointer; }
      .ig-linkbtn:hover { color: var(--ink); }
    `,
  ],
})
export class AccountComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  protected readonly user = this.auth.user;
  protected readonly loading = signal(true);
  protected readonly deletionRequested = signal(false);

  ngOnInit(): void {
    // Always re-fetch live so the balance/verification reflect server truth on entry.
    this.auth.loadMe().subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false),
    });
  }

  deletionMailto(): string {
    const email = this.user()?.email ?? '';
    const subject = encodeURIComponent(this.translate.instant('account.mailtoSubject'));
    const body = encodeURIComponent(this.translate.instant('account.mailtoBody', { email }));
    return `mailto:privacy@investguide.ua?subject=${subject}&body=${body}`;
  }

  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/']);
  }
}
