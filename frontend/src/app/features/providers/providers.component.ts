import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProviderService } from '../../core/catalog/provider.service';
import { Provider } from '../../core/catalog/provider.models';
import { ProviderCategory, RiskLevel } from '../../core/investment/investment.models';
import { formatMinorUnits } from '../../core/investment/money.util';

/** Maps backend enums to translation keys (resolved live so they re-translate on language switch). */
const CATEGORY_KEYS: Record<ProviderCategory, string> = {
  BANK_DEPOSIT: 'providers.categoryBankDeposit',
  GOV_BOND: 'providers.categoryGovBond',
  BROKER: 'providers.categoryBroker',
  FUND: 'providers.categoryFund',
  OTHER: 'providers.categoryOther',
};

const RISK_KEYS: Record<RiskLevel, string> = {
  LOW: 'providers.riskLow',
  MODERATE: 'providers.riskModerate',
  HIGH: 'providers.riskHigh',
};

/**
 * Providers transparency page (ticket FE-ACCT2, §5.1 `/providers`, §8.3).
 *
 * Read-only listing of the active catalog — the bounded universe recommendations are drawn from — so
 * users can see exactly which providers the advisor can pick. No edit affordances. Each entry links to
 * the provider's official source page.
 */
@Component({
  selector: 'ig-providers',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule],
  template: `
    <section class="ig-card">
      <h1>{{ 'providers.title' | translate }}</h1>
      <p class="ig-muted">{{ 'providers.intro' | translate }}</p>

      @if (loading()) {
        <p class="ig-muted">{{ 'providers.loading' | translate }}</p>
      } @else if (loadError()) {
        <div class="ig-alert ig-alert--error">{{ loadError() }}</div>
      } @else if (providers().length === 0) {
        <div class="ig-alert ig-alert--info">{{ 'providers.empty' | translate }}</div>
      } @else {
        <ul class="ig-providers">
          @for (p of providers(); track p.id) {
            <li class="ig-provider">
              <div class="ig-provider__head">
                <h2 class="ig-provider__name">{{ p.name }}</h2>
                <span class="ig-chip">{{ categoryLabel(p.category) | translate }}</span>
              </div>
              <p class="ig-provider__desc">{{ p.description }}</p>
              <dl class="ig-provider__meta">
                <div><dt>{{ 'providers.typicalReturn' | translate }}</dt><dd>{{ returnRange(p) }}</dd></div>
                <div><dt>{{ 'providers.risk' | translate }}</dt><dd>{{ riskLabel(p.riskLevel) | translate }}</dd></div>
                <div><dt>{{ 'providers.currencies' | translate }}</dt><dd>{{ p.currencies.join(', ') }}</dd></div>
                <div><dt>{{ 'providers.minAmount' | translate }}</dt><dd>{{ minAmount(p) }}</dd></div>
              </dl>
              <a class="ig-provider__src" [href]="p.sourceUrl" target="_blank" rel="noopener noreferrer">
                {{ 'common.officialSource' | translate }} ↗
              </a>
            </li>
          }
        </ul>
      }

      <p class="ig-back"><a routerLink="/search">{{ 'providers.backToSearch' | translate }}</a></p>
    </section>
  `,
  styles: [
    `
      .ig-providers { list-style: none; padding: 0; margin: 1.25rem 0 0; display: grid; gap: 1rem; }
      .ig-provider { border: 1px solid var(--ig-border); border-radius: 10px; padding: 1.1rem; }
      .ig-provider__head { display: flex; align-items: center; gap: 0.75rem; justify-content: space-between; }
      .ig-provider__name { font-size: 1.1rem; margin: 0; }
      .ig-chip {
        background: rgba(0, 87, 183, 0.08); color: var(--ig-blue);
        font-size: 0.75rem; font-weight: 700; padding: 0.15rem 0.6rem; border-radius: 999px; white-space: nowrap;
      }
      .ig-provider__desc { color: var(--ig-muted); margin: 0.5rem 0 0.75rem; }
      .ig-provider__meta {
        display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
        gap: 0.5rem 1rem; margin: 0 0 0.75rem;
      }
      .ig-provider__meta dt { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--ig-muted); }
      .ig-provider__meta dd { margin: 0.1rem 0 0; font-weight: 600; }
      .ig-provider__src { font-size: 0.85rem; font-weight: 700; text-decoration: none; color: var(--ig-blue); }
      .ig-back { margin-top: 1.5rem; }
      .ig-alert--info { background: rgba(0, 87, 183, 0.06); border: 1px solid var(--ig-border); border-radius: 8px; padding: 0.75rem 1rem; }
    `,
  ],
})
export class ProvidersComponent implements OnInit {
  private readonly providerService = inject(ProviderService);
  private readonly translate = inject(TranslateService);

  protected readonly providers = signal<Provider[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  ngOnInit(): void {
    this.providerService.list().subscribe({
      next: (list) => {
        this.providers.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(this.translate.instant('providers.loadError'));
        this.loading.set(false);
      },
    });
  }

  /** Returns the translation key for the category (resolved by the `translate` pipe in the template). */
  categoryLabel(category: ProviderCategory): string {
    return CATEGORY_KEYS[category] ?? category;
  }

  /** Returns the translation key for the risk level. */
  riskLabel(risk: RiskLevel): string {
    return RISK_KEYS[risk] ?? risk;
  }

  returnRange(p: Provider): string {
    const { min, max } = p.typicalReturnPct;
    return min === max ? `${min}%` : `${min}–${max}%`;
  }

  minAmount(p: Provider): string {
    const currency = p.currencies[0] === 'USD' ? 'USD' : 'UAH';
    return formatMinorUnits(p.minAmount, currency);
  }
}
