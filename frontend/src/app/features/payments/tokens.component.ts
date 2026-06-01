import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { PaymentService } from '../../core/payments/payment.service';
import { TokenPack } from '../../core/payments/payment.models';
import { formatMinorUnits } from '../../core/investment/money.util';
import { parseApiError } from '../../core/api/api-error.util';
import { PluralPipe } from '../../core/i18n/plural.pipe';

/** sessionStorage key carrying the paymentId across the monobank redirect to the result page. */
export const PENDING_PAYMENT_KEY = 'ig_pending_payment_id';

/**
 * Buy-tokens page (tickets FE-PAY1 + FE-PAY2, §9.1, §9.2, §4.3).
 *
 * Lists active packs from GET /tokens/packs with prices formatted from integer minor units (never
 * reconstructed via float math). "Buy" calls POST /payments and redirects the buyer to the
 * server-provided monobank hosted checkout (`pageUrl`) — the client never computes a signature or
 * touches the merchant token. The returned `paymentId` is stashed for FE-PAY3 to poll after the
 * redirect returns.
 */
@Component({
  selector: 'ig-tokens',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, PluralPipe],
  template: `
    <section class="ig-card">
      <h1>{{ 'tokens.title' | translate }}</h1>
      <p class="ig-muted">{{ 'tokens.intro' | translate: { balance: (auth.tokenBalance() | igPlural: 'token') } }}</p>

      @if (loading()) {
        <p class="ig-muted">{{ 'tokens.loadingPacks' | translate }}</p>
      } @else if (loadError()) {
        <div class="ig-alert ig-alert--error">{{ loadError() }}</div>
      } @else if (packs().length === 0) {
        <div class="ig-alert ig-alert--info">{{ 'tokens.empty' | translate }}</div>
      } @else {
        @if (buyError()) {
          <div class="ig-alert ig-alert--error">{{ buyError() }}</div>
        }
        <ul class="ig-packs">
          @for (pack of packs(); track pack.id) {
            <li class="ig-pack">
              <div class="ig-pack__tokens">{{ pack.tokens | igPlural: 'token' }}</div>
              <div class="ig-pack__price">{{ price(pack) }}</div>
              <div class="ig-pack__per">{{ 'tokens.perToken' | translate: { price: perToken(pack) } }}</div>
              <button
                type="button"
                class="ig-btn ig-btn--primary"
                [disabled]="buyingId() !== null"
                (click)="buy(pack)"
              >
                {{ (buyingId() === pack.id ? 'tokens.redirecting' : 'tokens.buy') | translate }}
              </button>
            </li>
          }
        </ul>
        <p class="ig-muted ig-fineprint">{{ 'tokens.fineprint' | translate }}</p>
      }

      <p class="ig-back"><a routerLink="/search">{{ 'tokens.backToSearch' | translate }}</a></p>
    </section>
  `,
  styles: [
    `
      .ig-packs {
        list-style: none;
        padding: 0;
        margin: 1rem 0 0;
        display: grid;
        gap: 1rem;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      }
      .ig-pack {
        border: 1px solid var(--ig-border);
        border-radius: 10px;
        padding: 1.1rem;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        align-items: flex-start;
      }
      .ig-pack__tokens { font-size: 1.15rem; font-weight: 700; }
      .ig-pack__price { font-size: 1.4rem; font-weight: 800; color: var(--ig-blue); }
      .ig-pack__per { font-size: 0.8rem; color: var(--ig-muted); }
      .ig-pack .ig-btn { margin-top: 0.5rem; }
      .ig-fineprint { font-size: 0.8rem; margin-top: 1rem; }
      .ig-back { margin-top: 1.5rem; }
      .ig-alert--info {
        background: rgba(0, 87, 183, 0.06);
        border: 1px solid var(--ig-border);
        border-radius: 8px;
        padding: 0.75rem 1rem;
      }
    `,
  ],
})
export class TokensComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly payments = inject(PaymentService);
  private readonly translate = inject(TranslateService);

  readonly packs = signal<TokenPack[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly buyingId = signal<string | null>(null);
  readonly buyError = signal<string | null>(null);

  ngOnInit(): void {
    this.payments.packs().subscribe({
      next: (packs) => {
        this.packs.set(packs);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(this.translate.instant('tokens.loadError'));
        this.loading.set(false);
      },
    });
  }

  price(pack: TokenPack): string {
    return formatMinorUnits(pack.priceMinorUnits, pack.currency);
  }

  /** Per-token price for display only — integer minor-unit division, formatted as currency. */
  perToken(pack: TokenPack): string {
    const perMinor = Math.round(pack.priceMinorUnits / pack.tokens);
    return formatMinorUnits(perMinor, pack.currency);
  }

  buy(pack: TokenPack): void {
    if (this.buyingId() !== null) {
      return;
    }
    this.buyError.set(null);
    this.buyingId.set(pack.id);
    this.payments.createPayment(pack.id).subscribe({
      next: (res) => {
        // Stash the paymentId so the result page can poll after monobank redirects back.
        sessionStorage.setItem(PENDING_PAYMENT_KEY, res.paymentId);
        // Top-level redirect to the monobank hosted checkout page.
        window.location.href = res.pageUrl;
      },
      error: (err) => {
        this.buyingId.set(null);
        this.buyError.set(parseApiError(err).message);
      },
    });
  }
}
