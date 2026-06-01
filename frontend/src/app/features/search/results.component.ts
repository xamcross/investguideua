import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { InvestmentOption, SearchResponse } from '../../core/investment/investment.models';
import { formatMinorUnits } from '../../core/investment/money.util';

/**
 * Reusable results renderer (tickets FE-SEARCH2, FE-HIST2): the list of options plus the mandatory
 * disclaimers. Shared by the live search page and the history detail page so both render identically,
 * including disclaimers. The financial disclaimer is ALWAYS shown when present in the response
 * (AC #5); the currency-risk disclaimer is shown only when the server included it.
 */
@Component({
  selector: 'ig-results',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    @if (result) {
      @if (result.options.length === 0) {
        <div class="ig-alert ig-alert--info">{{ 'results.noOptions' | translate }}</div>
      } @else {
        <ul class="ig-options">
          @for (opt of result.options; track opt.providerId + opt.instrument) {
            <li class="ig-option">
              <div class="ig-option__head">
                <span class="ig-option__name">{{ opt.providerName }}</span>
                <span class="ig-badge ig-badge--cat">{{ opt.category }}</span>
                <span class="ig-badge ig-badge--risk" [attr.data-risk]="opt.riskLevel">
                  {{ opt.riskLevel }}
                </span>
              </div>
              <div class="ig-option__instrument">{{ opt.instrument }}</div>
              <dl class="ig-option__facts">
                <div>
                  <dt>{{ 'results.expectedReturn' | translate }}</dt>
                  <dd>{{ opt.expectedReturnPct.min }}–{{ opt.expectedReturnPct.max }}% {{ 'common.perYear' | translate }}</dd>
                </div>
                <div>
                  <dt>{{ 'results.currency' | translate }}</dt>
                  <dd>{{ opt.currency }}</dd>
                </div>
                <div>
                  <dt>{{ 'results.minAmount' | translate }}</dt>
                  <dd>{{ formatMoney(opt) }}</dd>
                </div>
                <div>
                  <dt>{{ 'results.liquidity' | translate }}</dt>
                  <dd>{{ opt.liquidity || '—' }}</dd>
                </div>
              </dl>
              @if (opt.rationale) {
                <p class="ig-option__rationale">{{ opt.rationale }}</p>
              }
              <a class="ig-option__src" [href]="opt.sourceUrl" target="_blank" rel="noopener noreferrer">
                {{ 'common.officialSource' | translate }} ↗
              </a>
            </li>
          }
        </ul>
      }

      <div class="ig-disclaimer">
        <p>{{ result.disclaimer }}</p>
        @if (result.currencyRiskDisclaimer) {
          <p class="ig-disclaimer--currency">{{ result.currencyRiskDisclaimer }}</p>
        }
      </div>
    }
  `,
  styles: [
    `
      .ig-options { list-style: none; padding: 0; margin: 0 0 1rem; display: grid; gap: 0.75rem; }
      .ig-option { border: 1px solid var(--ig-border); border-radius: 10px; padding: 0.9rem 1rem; background: var(--ig-surface); }
      .ig-option__head { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
      .ig-option__name { font-weight: 700; font-size: 1.02rem; }
      .ig-badge { font-size: 0.72rem; font-weight: 700; padding: 0.12rem 0.5rem; border-radius: 999px; }
      .ig-badge--cat { background: rgba(0, 87, 183, 0.1); color: var(--ig-blue); }
      .ig-badge--risk { background: #eee; color: #333; }
      .ig-badge--risk[data-risk='LOW'] { background: #e6f4ea; color: #1e7a3c; }
      .ig-badge--risk[data-risk='MODERATE'] { background: #fdf2d6; color: #8a6d1a; }
      .ig-badge--risk[data-risk='HIGH'] { background: #fbe3e1; color: #b3261e; }
      .ig-option__instrument { margin: 0.4rem 0; color: var(--ig-ink); }
      .ig-option__facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.4rem 1rem; margin: 0.5rem 0; }
      .ig-option__facts dt { font-size: 0.72rem; color: var(--ig-muted); text-transform: uppercase; letter-spacing: 0.03em; }
      .ig-option__facts dd { margin: 0; font-weight: 600; font-size: 0.92rem; }
      .ig-option__rationale { margin: 0.4rem 0; color: var(--ig-muted); font-size: 0.92rem; }
      .ig-option__src { font-size: 0.85rem; font-weight: 600; color: var(--ig-blue); text-decoration: none; }
      .ig-disclaimer { border-top: 1px solid var(--ig-border); padding-top: 0.75rem; color: var(--ig-muted); font-size: 0.82rem; }
      .ig-disclaimer p { margin: 0.3rem 0; }
      .ig-disclaimer--currency { color: #8a6d1a; font-weight: 600; }
      .ig-alert--info { background: rgba(0, 87, 183, 0.06); border: 1px solid var(--ig-border); border-radius: 8px; padding: 0.75rem 1rem; }
    `,
  ],
})
export class ResultsComponent {
  @Input() result: SearchResponse | null = null;

  formatMoney(opt: InvestmentOption): string {
    return formatMinorUnits(opt.minAmount, opt.currency);
  }
}
