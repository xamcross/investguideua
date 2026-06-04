import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProviderService } from '../../core/catalog/provider.service';
import { Provider } from '../../core/catalog/provider.models';
import { ProviderCategory, RiskLevel } from '../../core/investment/investment.models';
import { formatMinorUnits } from '../../core/investment/money.util';
import { EmptyStateComponent } from '../shared/empty-state.component';
import { LoadingStateComponent } from '../shared/loading-state.component';
import { ErrorStateComponent } from '../shared/error-state.component';

/**
 * Display order of the instrument groups, mirroring the source instrument table (war bonds first,
 * highest-risk alternatives last). Providers whose category is somehow outside this list fall to the
 * end (defensive — every seeded provider has a known instrument).
 */
const INSTRUMENT_ORDER: ProviderCategory[] = [
  'MILITARY_BOND',
  'GOV_BOND',
  'CASH_CURRENCY',
  'PRECIOUS_METALS',
  'REAL_ESTATE',
  'INDEX_ETF',
  'FOREIGN_STOCKS',
  'CRYPTO',
  'CORPORATE_BOND',
  'CROWDLENDING',
  'PENSION_FUND',
  'LIFE_INSURANCE',
  'BUSINESS_EQUITY',
];

/** One instrument section: its category key (resolved live via `category.*`) and its providers. */
interface InstrumentGroup {
  category: ProviderCategory;
  providers: Provider[];
}

/**
 * Providers transparency page (ticket FE-ACCT2, §5.1 `/providers`, §8.3).
 *
 * Read-only listing of the active catalog — the bounded universe recommendations are drawn from —
 * grouped by investment instrument so users can see, per instrument, exactly which providers the
 * advisor can pick. No edit affordances. Each entry links to the provider's official source page.
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
        @for (group of groups(); track group.category) {
          <div class="ig-instr">
            <h2 class="ig-instr__title">{{ categoryLabel(group.category) | translate }}</h2>
            <ul class="ig-options">
              @for (p of group.providers; track p.id) {
                <li class="ig-opt">
                  <div class="ig-opt__head">
                    <span class="ig-opt__name">{{ p.name }}</span>
                    <span class="ig-opt__badges">
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
          </div>
        }
      }

      <p class="ig-back"><a routerLink="/search">{{ 'providers.backToSearch' | translate }}</a></p>
    </section>
  `,
  styles: [
    `
      .ig-instr { margin-top: 2rem; }
      .ig-instr:first-of-type { margin-top: 1rem; }
      .ig-instr__title { margin: 0 0 0.75rem; font-size: 1.1rem; }
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

  /** Active catalog grouped by instrument, in {@link INSTRUMENT_ORDER}; empty groups are dropped. */
  protected readonly groups = computed<InstrumentGroup[]>(() => {
    const byCategory = new Map<ProviderCategory, Provider[]>();
    for (const p of this.providers()) {
      const list = byCategory.get(p.category);
      if (list) {
        list.push(p);
      } else {
        byCategory.set(p.category, [p]);
      }
    }
    const ordered = [...byCategory.keys()].sort(
      (a, b) => orderIndex(a) - orderIndex(b),
    );
    return ordered.map((category) => ({ category, providers: byCategory.get(category)! }));
  });

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

  /** Returns the translation key for the instrument (resolved by the `translate` pipe). */
  categoryLabel(category: ProviderCategory): string {
    return `category.${category}`;
  }

  /** Returns the translation key for the risk level. */
  riskLabel(risk: RiskLevel): string {
    return `risk.${risk}`;
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

/** Position of a category in {@link INSTRUMENT_ORDER}; unknown categories sort to the end. */
function orderIndex(category: ProviderCategory): number {
  const i = INSTRUMENT_ORDER.indexOf(category);
  return i === -1 ? INSTRUMENT_ORDER.length : i;
}
