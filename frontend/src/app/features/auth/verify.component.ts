import { ChangeDetectionStrategy, Component, Input, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { parseApiError } from '../../core/api/api-error.util';
import { PluralPipe } from '../../core/i18n/plural.pipe';

type VerifyState = 'verifying' | 'success' | 'already' | 'invalid' | 'missing';

/**
 * Email verification page (ticket FE-AUTH3, §4.1.2, AC #1).
 *
 * Consumes the `?token=` from the verification link (bound via component input binding) and calls
 * POST /auth/verify. On first success it reflects emailVerified=true and the new tokenBalance (5).
 * Re-visiting after verifying shows a benign "already verified" state (no extra tokens). Expired or
 * used links show a clear, non-alarming message.
 */
@Component({
  selector: 'ig-verify',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, PluralPipe],
  template: `
    <section class="ig-card ig-auth">
      <h1>{{ 'verify.title' | translate }}</h1>

      @switch (state()) {
        @case ('verifying') {
          <p class="ig-muted">{{ 'verify.verifying' | translate }}</p>
        }
        @case ('success') {
          <div class="ig-alert ig-alert--success">
            {{ 'verify.success' | translate: { tokens: (balance() | igPlural: 'token') } }}
          </div>
          <p><a routerLink="/login">{{ 'verify.signInToStart' | translate }}</a></p>
        }
        @case ('already') {
          <div class="ig-alert ig-alert--success">{{ 'verify.already' | translate }}</div>
          <p class="ig-muted">{{ 'verify.noAdditional' | translate }}</p>
          <p><a routerLink="/login">{{ 'verify.goToSignIn' | translate }}</a></p>
        }
        @case ('invalid') {
          <div class="ig-alert ig-alert--error">{{ errorMessage() || ('verify.defaultError' | translate) }}</div>
          <p class="ig-muted">{{ 'verify.invalidHint' | translate }}</p>
          <p><a routerLink="/register">{{ 'verify.backToRegister' | translate }}</a></p>
        }
        @case ('missing') {
          <div class="ig-alert ig-alert--error">{{ 'verify.missing' | translate }}</div>
          <p><a routerLink="/register">{{ 'verify.backToRegister' | translate }}</a></p>
        }
      }
    </section>
  `,
  styles: [`.ig-auth { max-width: 480px; margin: 0 auto; } .ig-auth h1 { margin-top: 0; }`],
})
export class VerifyComponent implements OnInit {
  private readonly auth = inject(AuthService);

  /** Bound from the `?token=` query param (withComponentInputBinding). */
  @Input() token?: string;

  readonly state = signal<VerifyState>('verifying');
  readonly balance = signal(0);
  /** Empty by default; the invalid state shows the server message, else a translated fallback. */
  readonly errorMessage = signal('');

  ngOnInit(): void {
    const token = (this.token ?? '').trim();
    if (!token) {
      this.state.set('missing');
      return;
    }
    this.auth.verify(token).subscribe({
      next: (res) => {
        this.balance.set(res.tokenBalance);
        this.state.set(res.firstVerification ? 'success' : 'already');
      },
      error: (err) => {
        this.errorMessage.set(parseApiError(err).message);
        this.state.set('invalid');
      },
    });
  }
}
