import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnDestroy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { PaymentService } from '../../core/payments/payment.service';
import { PaymentStatusResponse } from '../../core/payments/payment.models';
import { PENDING_PAYMENT_KEY } from './tokens.component';

type ResultState = 'polling' | 'success' | 'failed' | 'processing' | 'missing';

/**
 * Payment result / confirmation page (ticket FE-PAY3, §4.3.4).
 *
 * monobank redirects here (redirectUrl) after checkout. Crediting is async and server-driven by the
 * verified server-to-server webhook (BE-P4), so we poll GET /payments/{paymentId} with bounded
 * backoff. On `success` we refresh the live balance from /me; on `failed` we offer a retry. If the
 * callback hasn't landed before the poll budget is exhausted we show a non-alarming "still
 * processing" terminal state rather than an error — the balance will update once the callback lands.
 * The client never credits locally.
 */
@Component({
  selector: 'ig-payment-result',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="ig-card ig-result">
      <h1>Payment status</h1>

      @switch (state()) {
        @case ('polling') {
          <p class="ig-muted">Confirming your payment...</p>
          <p class="ig-muted ig-fineprint">This can take a few seconds.</p>
        }
        @case ('success') {
          <div class="ig-alert ig-alert--success">
            Payment confirmed. <strong>{{ payment()?.tokensToCredit }} tokens</strong> have been added.
            @if (balanceRefreshed()) {
              Your balance is now <strong>{{ auth.tokenBalance() }}</strong>.
            }
          </div>
          <p><a class="ig-btn ig-btn--primary" routerLink="/search">Start a search</a></p>
        }
        @case ('failed') {
          <div class="ig-alert ig-alert--error">
            Your payment did not go through. No tokens were added and you were not charged.
          </div>
          <p><a class="ig-btn ig-btn--primary" routerLink="/tokens">Try again</a></p>
        }
        @case ('processing') {
          <div class="ig-alert ig-alert--info">
            Still processing — we'll update your balance shortly. You can safely leave this page; your
            tokens will appear once the payment is confirmed.
          </div>
          <p>
            <a class="ig-btn ig-btn--ghost" routerLink="/account">View account</a>
            <a class="ig-btn ig-btn--ghost" routerLink="/search">Go to search</a>
          </p>
        }
        @case ('missing') {
          <div class="ig-alert ig-alert--info">
            We couldn't find a payment to confirm. If you just paid, check your balance on the
            <a routerLink="/account">account page</a>.
          </div>
          <p><a routerLink="/tokens">Back to buy tokens</a></p>
        }
      }
    </section>
  `,
  styles: [
    `
      .ig-result { max-width: 520px; margin: 0 auto; }
      .ig-result h1 { margin-top: 0; }
      .ig-fineprint { font-size: 0.8rem; }
      .ig-result .ig-btn { margin-right: 0.5rem; }
      .ig-alert--info {
        background: rgba(0, 87, 183, 0.06);
        border: 1px solid var(--ig-border);
        border-radius: 8px;
        padding: 0.75rem 1rem;
      }
    `,
  ],
})
export class PaymentResultComponent implements OnInit, OnDestroy {
  protected readonly auth = inject(AuthService);
  private readonly payments = inject(PaymentService);

  /** Optional ?paymentId= override (else read from sessionStorage). Bound via input binding. */
  @Input() paymentId?: string;

  readonly state = signal<ResultState>('polling');
  readonly payment = signal<PaymentStatusResponse | null>(null);
  /** True once /me has been re-fetched, so the success card only asserts a balance it actually has. */
  readonly balanceRefreshed = signal(false);

  /** Bounded backoff (ms): ~9 attempts over ~30s before the "still processing" terminal state. */
  private static readonly BACKOFF_MS = [1000, 1000, 2000, 2000, 3000, 4000, 5000, 6000, 6000];
  private attempt = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private destroyed = false;
  private resolvedId: string | null = null;

  ngOnInit(): void {
    this.resolvedId =
      this.paymentId || sessionStorage.getItem(PENDING_PAYMENT_KEY) || null;
    if (!this.resolvedId) {
      this.state.set('missing');
      return;
    }
    this.poll();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    if (this.timer) {
      clearTimeout(this.timer);
    }
  }

  private poll(): void {
    if (this.destroyed || !this.resolvedId) {
      return;
    }
    this.payments.status(this.resolvedId).subscribe({
      next: (res) => {
        if (this.destroyed) {
          return; // a late response after navigation away must not write state or refetch.
        }
        this.payment.set(res);
        if (res.status === 'success') {
          this.finishSuccess();
        } else if (res.status === 'failed' || res.status === 'reversed') {
          sessionStorage.removeItem(PENDING_PAYMENT_KEY);
          this.state.set('failed');
        } else {
          this.scheduleNext();
        }
      },
      // A transient read error shouldn't end the flow — keep polling within the budget.
      error: () => {
        if (this.destroyed) {
          return;
        }
        this.scheduleNext();
      },
    });
  }

  private scheduleNext(): void {
    if (this.attempt >= PaymentResultComponent.BACKOFF_MS.length) {
      // Poll budget exhausted: crediting may still arrive via the async callback (BE-P4).
      this.state.set('processing');
      return;
    }
    const delay = PaymentResultComponent.BACKOFF_MS[this.attempt++];
    this.timer = setTimeout(() => this.poll(), delay);
  }

  private finishSuccess(): void {
    sessionStorage.removeItem(PENDING_PAYMENT_KEY);
    // Refresh the authoritative balance from the server; never credit locally. The status is already
    // 'success' (the server credited), so we always land on the success state — but we only assert a
    // concrete balance number if the /me refresh actually returned one (loadMe yields null on failure).
    this.auth.loadMe().subscribe((user) => {
      if (this.destroyed) {
        return;
      }
      this.balanceRefreshed.set(user !== null);
      this.state.set('success');
    });
  }
}
