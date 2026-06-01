import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { PluralPipe } from '../../core/i18n/plural.pipe';

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
  imports: [RouterLink, TranslateModule, PluralPipe],
  template: `
    <section class="ig-card">
      <h1>{{ 'account.title' | translate }}</h1>

      @if (loading()) {
        <p class="ig-muted">{{ 'account.loading' | translate }}</p>
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
            {{ u.tokenBalance | igPlural: 'token' }}
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
        <div class="ig-alert ig-alert--error">
          {{ 'account.loadError' | translate }} <a routerLink="/login">{{ 'account.signInAgain' | translate }}</a>.
        </div>
      }
    </section>
  `,
  styles: [
    `
      .ig-profile {
        display: grid;
        grid-template-columns: auto 1fr;
        gap: 0.5rem 1.25rem;
        margin: 1rem 0 1.5rem;
        align-items: baseline;
      }
      .ig-profile dt { color: var(--ig-muted); font-weight: 600; }
      .ig-profile dd { margin: 0; }
      .ig-badge {
        display: inline-block; padding: 0.1rem 0.55rem; border-radius: 999px;
        font-size: 0.78rem; font-weight: 700;
      }
      .ig-badge--ok { background: rgba(39, 174, 96, 0.12); color: #1e8449; }
      .ig-badge--warn { background: rgba(255, 213, 0, 0.18); color: #8a6d00; }
      .ig-inline-link { margin-left: 0.6rem; font-size: 0.85rem; }
      .ig-actions { margin-bottom: 2rem; }
      .ig-danger-zone {
        border-top: 1px solid var(--ig-border);
        padding-top: 1.25rem;
      }
      .ig-danger-zone h2 { font-size: 1.05rem; margin: 0 0 0.5rem; }
      .ig-btn--ghost {
        background: none; border: 1px solid var(--ig-border); color: var(--ig-ink);
        padding: 0.4rem 0.9rem; border-radius: 8px; text-decoration: none; display: inline-block;
        margin-right: 0.75rem;
      }
      .ig-linkbtn { background: none; border: none; color: var(--ig-muted); font: inherit; cursor: pointer; }
      .ig-alert--info {
        background: rgba(0, 87, 183, 0.06); border: 1px solid var(--ig-border);
        border-radius: 8px; padding: 0.75rem 1rem; margin-top: 0.75rem;
      }
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
