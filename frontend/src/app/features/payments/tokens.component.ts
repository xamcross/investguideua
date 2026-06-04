import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { PaymentService } from '../../core/payments/payment.service';
import { TokenPack } from '../../core/payments/payment.models';
import { formatMinorUnits } from '../../core/investment/money.util';
import { parseApiError } from '../../core/api/api-error.util';
import { PluralPipe } from '../../core/i18n/plural.pipe';
import { EmptyStateComponent } from '../shared/empty-state.component';
import { LoadingStateComponent } from '../shared/loading-state.component';
import { ErrorStateComponent } from '../shared/error-state.component';

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
  imports: [RouterLink, TranslateModule, PluralPipe, EmptyStateComponent, LoadingStateComponent, ErrorStateComponent],
  template: `
    <section class="ig-tokens reveal d1">
      <p class="ig-kicker ig-kicker--gold">{{ 'tokens.kicker' | translate }}</p>
      <h1>{{ 'tokens.title' | translate }}</h1>
      <p class="ig-tokens__lead">{{ 'tokens.intro' | translate: { balance: (auth.tokenBalance() | igPlural: 'token') } }}</p>

      @if (loading()) {
        <ig-loading-state [label]="'tokens.loadingPacks' | translate" />
      } @else if (loadError()) {
        <ig-error-state [message]="loadError()!" />
      } @else if (packs().length === 0) {
        <ig-empty-state [message]="'tokens.empty' | translate" />
      } @else {
        @if (buyError()) {
          <ig-error-state [message]="buyError()!" />
        }
        <ul class="ig-packs">
          @for (pack of packs(); track pack.id; let i = $index) {
            <li class="ig-pack" [class.is-recommended]="isRecommended(i)">
              @if (isRecommended(i)) {
                <span class="ig-pack__ribbon" aria-hidden="true">{{ 'tokens.recommended' | translate }}</span>
                <span class="ig-sr-only">{{ 'tokens.recommendedSr' | translate }}</span>
              }
              <div class="ig-pack__tokens">{{ pack.tokens | igPlural: 'token' }}</div>
              <div class="ig-pack__price">{{ price(pack) }}</div>
              <div class="ig-pack__per">{{ 'tokens.perToken' | translate: { price: perToken(pack) } }}</div>
              <button
                type="button"
                class="ig-btn"
                [class.ig-btn--gold]="isRecommended(i)"
                [class.ig-btn--primary]="!isRecommended(i)"
                [disabled]="buyingId() !== null"
                (click)="buy(pack)"
              >
                {{ (buyingId() === pack.id ? 'tokens.redirecting' : 'tokens.buy') | translate }}
              </button>
            </li>
          }
        </ul>
        <p class="ig-tokens__fine">{{ 'tokens.fineprint' | translate }}</p>
      }

      <p class="ig-back"><a routerLink="/search">{{ 'tokens.backToSearch' | translate }}</a></p>
    </section>
  `,
  styles: [
    `
      .ig-tokens {
        position: relative; overflow: hidden; border-radius: var(--radius);
        padding: 2rem 1.75rem; margin-bottom: 1.25rem; color: rgba(255, 255, 255, .74);
        background: radial-gradient(90% 70% at 85% -10%, rgba(217, 168, 35, .14), transparent 55%), var(--navy-900);
        box-shadow: var(--shadow-lg);
      }
      .ig-tokens h1 { color: #fff; }
      .ig-kicker--gold { color: var(--gold-300); }
      .ig-tokens__lead { color: rgba(255, 255, 255, .74); max-width: 52ch; }
      .ig-packs {
        list-style: none; padding: 0; margin: 1.5rem 0 0;
        display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      }
      .ig-pack {
        position: relative; overflow: hidden;
        border: 1px solid rgba(255, 255, 255, .14); border-radius: var(--radius-sm);
        background: rgba(255, 255, 255, .04); padding: 1.25rem;
        display: flex; flex-direction: column; gap: .35rem; align-items: flex-start;
      }
      .ig-pack.is-recommended { border-color: var(--gold-500); background: rgba(217, 168, 35, .08); }
      .ig-pack__ribbon {
        position: absolute; top: .9rem; right: -2.4rem; transform: rotate(45deg);
        background: linear-gradient(180deg, var(--gold-500), var(--gold-600)); color: var(--navy-900);
        font-family: var(--font-mono); font-size: .6rem; font-weight: 700; letter-spacing: .08em;
        text-transform: uppercase; padding: .2rem 2.5rem;
      }
      .ig-pack__tokens { font-family: var(--font-mono); font-size: 1.15rem; font-weight: 700; color: var(--gold-300); }
      .ig-pack__price { font-family: var(--font-display); font-size: 1.7rem; font-weight: 800; color: #fff; }
      .ig-pack__per { font-family: var(--font-mono); font-size: .78rem; color: rgba(255, 255, 255, .6); }
      .ig-pack .ig-btn { margin-top: .65rem; align-self: stretch; }
      .ig-tokens__fine { font-size: .8rem; margin-top: 1.25rem; color: rgba(255, 255, 255, .55); }
      .ig-back { margin-top: 1.5rem; }
      .ig-back a { color: var(--gold-100); }
      @media (max-width: 900px) { .ig-packs { grid-template-columns: 1fr; } }
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

  /** Display-only: the middle pack of exactly three is highlighted as recommended (no model change). */
  isRecommended(index: number): boolean {
    return this.packs().length === 3 && index === 1;
  }

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
