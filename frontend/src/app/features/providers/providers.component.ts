import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProviderService } from '../../core/catalog/provider.service';
import { Provider } from '../../core/catalog/provider.models';
import { ProviderCategory, RiskLevel } from '../../core/investment/investment.models';
import { formatMinorUnits } from '../../core/investment/money.util';
import { EmptyStateComponent } from '../shared/empty-state.component';
import { LoadingStateComponent } from '../shared/loading-state.component';
import { ErrorStateComponent } from '../shared/error-state.component';

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
  imports: [RouterLink, TranslateModule, EmptyStateComponent, LoadingStateComponent, ErrorStateComponent],
  template: `
    <section class="ig-card">
      <div class="ig-page-head">
        <h1 class="ig-display">{{ 'providers.title' | translate }}</h1>
        <p class="ig-muted">{{ 'providers.intro' | translate }}</p>
      </div>

      @if (loading()) {
        <ig-loading-state [label]="'providers.loading' | translate" />
      } @else if (loadError()) {
        <ig-error-state [message]="loadError()!" />
      } @else if (providers().length === 0) {
        <ig-empty-state [message]="'providers.empty' | translate" />
      } @else {
        <ul class="ig-options">
          @for (p of providers(); track p.id) {
            <li class="ig-opt">
              <div class="ig-opt__head">
                <span class="ig-opt__name">{{ p.name }}</span>
                <span class="ig-opt__badges">
                  <span class="ig-badge ig-badge--cat">{{ categoryLabel(p.category) | translate }}</span>
                  <span class="ig-badge ig-badge--risk" [attr.data-risk]="p.riskLevel">{{ riskLabel(p.riskLevel) | translate }}</span>
                </span>
              </div>
              <div class="ig-opt__return">
                <span class="ig-opt__fig">{{ returnRange(p) }}</span>
                <span class="ig-opt__unit">{{ 'common.perYear' | translate }}</span>
                <span class="ig-opt__lbl">{{ 'providers.typicalReturn' | translate }}</span>
              </div>
              <p class="ig-opt__rationale">{{ p.description }}</p>
              <dl class="ig-fact-grid">
                <div class="ig-fact"><dt>{{ 'providers.currencies' | translate }}</dt><dd>{{ currencyList(p) }}</dd></div>
                <div class="ig-fact"><dt>{{ 'providers.minAmount' | translate }}</dt><dd>{{ minAmount(p) }}</dd></div>
              </dl>
              <a class="ig-opt__src" [href]="p.sourceUrl" target="_blank" rel="noopener noreferrer">
                {{ 'common.officialSource' | translate }}
                <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor"
                     stroke-width="2" aria-hidden="true" focusable="false"><path d="M6 3h7v7M13 3L4 12"/></svg>
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
      .ig-back { margin-top: 1.5rem; }
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

  /** Display-only: localize the UAH currency word ('грн'/'UAH') and join; no math change. */
  currencyList(p: Provider): string {
    return p.currencies
      .map((c) => (c === 'UAH' ? this.translate.instant('common.currencyUahShort') : c))
      .join(', ');
  }

  minAmount(p: Provider): string {
    const currency = p.currencies[0] === 'USD' ? 'USD' : 'UAH';
    return formatMinorUnits(p.minAmount, currency);
  }
}
