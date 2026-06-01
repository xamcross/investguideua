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
        <h1>{{ 'historyDetail.notFoundTitle' | translate }}</h1>
        <p class="ig-muted">{{ 'historyDetail.notFoundBody' | translate }}</p>
        <p><a routerLink="/history">{{ 'historyDetail.backToHistory' | translate }}</a></p>
      } @else if (result()) {
        <p><a routerLink="/history">← {{ 'historyDetail.backToHistoryArrow' | translate }}</a></p>
        <h1>{{ result()!.amount / 100 | number: '1.2-2' }} {{ result()!.currency }}</h1>
        <ig-results [result]="result()!" />
      } @else if (error()) {
        <div class="ig-alert ig-alert--error">{{ error() }}</div>
      }
    </section>
  `,
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
