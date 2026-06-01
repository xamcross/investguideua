import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProviderService } from '../../core/catalog/provider.service';
import { Provider } from '../../core/catalog/provider.models';
import { ProviderCategory, RiskLevel } from '../../core/investment/investment.models';
import { formatMinorUnits } from '../../core/investment/money.util';

const CATEGORY_LABELS: Record<ProviderCategory, string> = {
  BANK_DEPOSIT: 'Bank deposit',
  GOV_BOND: 'Government bond',
  BROKER: 'Broker',
  FUND: 'Fund',
  OTHER: 'Other',
};

const RISK_LABELS: Record<RiskLevel, string> = {
  LOW: 'Low',
  MODERATE: 'Moderate',
  HIGH: 'High',
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
  imports: [RouterLink],
  template: `
    <section class="ig-card">
      <h1>Provider catalog</h1>
      <p class="ig-muted">
        These are the active providers our recommendations are drawn from. Recommendations never go
        outside this list. Figures are indicative — always confirm on the provider's official page.
      </p>

      @if (loading()) {
        <p class="ig-muted">Loading providers…</p>
      } @else if (loadError()) {
        <div class="ig-alert ig-alert--error">{{ loadError() }}</div>
      } @else if (providers().length === 0) {
        <div class="ig-alert ig-alert--info">No providers are listed right now.</div>
      } @else {
        <ul class="ig-providers">
          @for (p of providers(); track p.id) {
            <li class="ig-provider">
              <div class="ig-provider__head">
                <h2 class="ig-provider__name">{{ p.name }}</h2>
                <span class="ig-chip">{{ categoryLabel(p.category) }}</span>
              </div>
              <p class="ig-provider__desc">{{ p.description }}</p>
              <dl class="ig-provider__meta">
                <div><dt>Typical return</dt><dd>{{ returnRange(p) }}</dd></div>
                <div><dt>Risk</dt><dd>{{ riskLabel(p.riskLevel) }}</dd></div>
                <div><dt>Currencies</dt><dd>{{ p.currencies.join(', ') }}</dd></div>
                <div><dt>Min amount</dt><dd>{{ minAmount(p) }}</dd></div>
              </dl>
              <a class="ig-provider__src" [href]="p.sourceUrl" target="_blank" rel="noopener noreferrer">
                Official source ↗
              </a>
            </li>
          }
        </ul>
      }

      <p class="ig-back"><a routerLink="/search">Back to search</a></p>
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
        this.loadError.set('Could not load the provider catalog. Please try again.');
        this.loading.set(false);
      },
    });
  }

  categoryLabel(category: ProviderCategory): string {
    return CATEGORY_LABELS[category] ?? category;
  }

  riskLabel(risk: RiskLevel): string {
    return RISK_LABELS[risk] ?? risk;
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
