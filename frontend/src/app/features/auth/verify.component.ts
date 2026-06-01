import { ChangeDetectionStrategy, Component, Input, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { parseApiError } from '../../core/api/api-error.util';

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
  imports: [RouterLink],
  template: `
    <section class="ig-card ig-auth">
      <h1>Email verification</h1>

      @switch (state()) {
        @case ('verifying') {
          <p class="ig-muted">Verifying your email...</p>
        }
        @case ('success') {
          <div class="ig-alert ig-alert--success">
            Your email is verified. <strong>{{ balance() }} tokens</strong> have been added to your
            account.
          </div>
          <p><a routerLink="/login">Sign in to start</a></p>
        }
        @case ('already') {
          <div class="ig-alert ig-alert--success">This email is already verified.</div>
          <p class="ig-muted">No additional tokens were added.</p>
          <p><a routerLink="/login">Go to sign in</a></p>
        }
        @case ('invalid') {
          <div class="ig-alert ig-alert--error">{{ errorMessage() }}</div>
          <p class="ig-muted">
            The link may be expired or already used. Register again to receive a fresh link.
          </p>
          <p><a routerLink="/register">Back to register</a></p>
        }
        @case ('missing') {
          <div class="ig-alert ig-alert--error">No verification token was provided in the link.</div>
          <p><a routerLink="/register">Back to register</a></p>
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
  readonly errorMessage = signal('This verification link is invalid or has expired.');

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
