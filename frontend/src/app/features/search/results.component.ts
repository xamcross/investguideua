import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { InvestmentOption, SearchResponse } from '../../core/investment/investment.models';
import { formatMinorUnits } from '../../core/investment/money.util';
import { CurrencyLabelPipe } from '../../core/i18n/currency-label.pipe';
import { EmptyStateComponent } from '../shared/empty-state.component';

/**
 * Reusable results renderer (tickets FE-SEARCH2, FE-HIST2): the list of options plus the mandatory
 * disclaimers. Shared by the live search page and the history detail page so both render identically,
 * including disclaimers. The financial disclaimer is ALWAYS shown when present in the response
 * (AC #5); the currency-risk disclaimer is shown only when the server included it.
 *
 * Restyle note: all card/badge/disclaimer rules are GLOBAL (styles.css) so this component's styles[]
 * is intentionally empty - this guarantees Search and History-detail render identically.
 */
@Component({
  selector: 'ig-results',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslateModule, CurrencyLabelPipe, EmptyStateComponent],
  template: `
    @if (result) {
      @if (result.options.length === 0) {
        <ig-empty-state [message]="'results.noOptions' | translate" />
      } @else {
        <ul class="ig-options">
          @for (opt of result.options; track opt.providerId + opt.instrument; let i = $index) {
            <li [class]="'ig-opt reveal d' + delay(i)">
              <div class="ig-opt__head">
                <span class="ig-opt__name">{{ opt.providerName }}</span>
                <span class="ig-opt__badges">
                  <span class="ig-badge ig-badge--cat">{{ 'category.' + opt.category | translate }}</span>
                  <span class="ig-badge ig-badge--risk" [attr.data-risk]="opt.riskLevel">
                    {{ 'risk.' + opt.riskLevel | translate }}
                  </span>
                </span>
              </div>
              <div class="ig-opt__instrument">{{ opt.instrument }}</div>
              <div class="ig-opt__return">
                <span class="ig-opt__fig">{{ opt.expectedReturnPct.min }}&ndash;{{ opt.expectedReturnPct.max }}</span>
                <span class="ig-opt__unit">% {{ 'common.perYear' | translate }}</span>
                <span class="ig-opt__lbl">{{ 'results.expectedReturn' | translate }}</span>
              </div>
              <dl class="ig-fact-grid">
                <div class="ig-fact">
                  <dt>{{ 'results.currency' | translate }}</dt>
                  <dd>{{ opt.currency | igCurrency }}</dd>
                </div>
                <div class="ig-fact">
                  <dt>{{ 'results.minAmount' | translate }}</dt>
                  <dd>{{ formatMoney(opt) }}</dd>
                </div>
                <div class="ig-fact">
                  <dt>{{ 'results.liquidity' | translate }}</dt>
                  <dd>{{ opt.liquidity || ('common.dash' | translate) }}</dd>
                </div>
              </dl>
              @if (opt.rationale) {
                <p class="ig-opt__rationale">{{ opt.rationale }}</p>
              }
              <a class="ig-opt__src" [href]="opt.sourceUrl" target="_blank" rel="noopener noreferrer">
                {{ 'common.officialSource' | translate }}
                <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor"
                     stroke-width="2" aria-hidden="true" focusable="false"><path d="M6 3h7v7M13 3L4 12"/></svg>
              </a>
            </li>
          }
        </ul>
      }

      <div class="ig-disclaimer" role="note">
        <p>{{ result.disclaimer }}</p>
        @if (result.currencyRiskDisclaimer) {
          <p class="ig-disclaimer--currency">{{ result.currencyRiskDisclaimer }}</p>
        }
      </div>
    }
  `,
  styles: [],
})
export class ResultsComponent {
  @Input() result: SearchResponse | null = null;

  /** Staggered reveal delay class index, capped at 5 (matches global .reveal .d1..d5). */
  delay(i: number): number {
    return Math.min(i + 1, 5);
  }

  formatMoney(opt: InvestmentOption): string {
    return formatMinorUnits(opt.minAmount, opt.currency);
  }
}
