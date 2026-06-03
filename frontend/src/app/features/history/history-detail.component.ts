import { ChangeDetectionStrategy, Component, Input, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { InvestmentService } from '../../core/investment/investment.service';
import { SearchResponse } from '../../core/investment/investment.models';
import { parseApiError } from '../../core/api/api-error.util';
import { ResultsComponent } from '../search/results.component';

/**
 * Single past-search detail (ticket FE-HIST2, §5.4, §5.1). Reuses the {@link ResultsComponent} renderer
 * so options + disclaimers render identically to a fresh search. A non-owned/unknown id returns 404
 * from the backend and is shown as a clean not-found state.
 */
@Component({
  selector: 'ig-history-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ResultsComponent, DecimalPipe, TranslateModule],
  template: `
    <section class="ig-card">
      @if (loading()) {
        <p class="ig-muted">{{ 'common.loading' | translate }}</p>
      } @else if (notFound()) {
        <div class="ig-empty">
          <div class="ig-empty__mark" aria-hidden="true"></div>
          <h1>{{ 'historyDetail.notFoundTitle' | translate }}</h1>
          <p>{{ 'historyDetail.notFoundBody' | translate }}</p>
          <div class="ig-empty__actions">
            <a routerLink="/history" class="ig-btn ig-btn--ghost">{{ 'historyDetail.backToHistory' | translate }}</a>
          </div>
        </div>
      } @else if (result()) {
        <a class="ig-back-link" routerLink="/history">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor"
               stroke-width="2" aria-hidden="true" focusable="false"><path d="M10 3L5 8l5 5"/></svg>
          {{ 'historyDetail.backToHistoryArrow' | translate }}
        </a>
        <h1 class="ig-detail__amount">
          {{ result()!.amount / 100 | number: '1.2-2' }}
          <span class="ig-detail__cur">{{ result()!.currency }}</span>
        </h1>
        <ig-results [result]="result()!" />
      } @else if (error()) {
        <div class="ig-alert ig-alert--error" role="alert">{{ error() }}</div>
      }
    </section>
  `,
  styles: [
    `
      .ig-back-link {
        display: inline-flex; align-items: center; gap: .4rem; margin-bottom: 1rem;
        font-family: var(--font-mono); font-size: .8rem; font-weight: 700; color: var(--muted);
        text-decoration: none;
      }
      .ig-back-link:hover { color: var(--blue-600); text-decoration: none; }
      .ig-back-link svg { transition: transform .2s var(--ease); }
      .ig-back-link:hover svg { transform: translateX(-3px); }
      .ig-detail__amount { font-family: var(--font-display); }
      .ig-detail__cur { font-family: var(--font-mono); font-size: 1rem; font-weight: 700; color: var(--muted); }
      @media (prefers-reduced-motion: reduce) { .ig-back-link svg { transition: none; } }
    `,
  ],
})
export class HistoryDetailComponent implements OnInit {
  private readonly investments = inject(InvestmentService);
  private readonly translate = inject(TranslateService);

  /** Bound from the `:id` route param (withComponentInputBinding). */
  @Input() id?: string;

  readonly result = signal<SearchResponse | null>(null);
  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const id = (this.id ?? '').trim();
    if (!id) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }
    this.investments.getById(id).subscribe({
      next: (res) => {
        this.result.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        // Owner-scoped 404: prefer the typed envelope code, but also fall back to a raw 404 status in
        // case the body is missing, so a not-found id always lands on the clean not-found state.
        const is404 =
          parseApiError(err).code === 'NOT_FOUND' ||
          (err instanceof HttpErrorResponse && err.status === 404);
        if (is404) {
          this.notFound.set(true);
        } else {
          this.error.set(this.translate.instant('historyDetail.loadError'));
        }
      },
    });
  }
}
