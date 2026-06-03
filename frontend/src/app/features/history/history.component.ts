import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { InvestmentService } from '../../core/investment/investment.service';
import { HistoryPage } from '../../core/investment/investment.models';
import { PluralPipe } from '../../core/i18n/plural.pipe';

/**
 * Paginated search history (ticket FE-HIST1, §4.4, §5.1). Newest first, owner-scoped by the backend.
 * Each row links to the detail view. Empty history shows a friendly empty state.
 */
@Component({
  selector: 'ig-history',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, DecimalPipe, TranslateModule, PluralPipe],
  template: `
    <section class="ig-card">
      <div class="ig-page-head">
        <p class="ig-kicker">{{ 'history.kicker' | translate }}</p>
        <h1 class="ig-display">{{ 'history.title' | translate }}</h1>
      </div>

      @if (loading()) {
        <p class="ig-muted">{{ 'common.loading' | translate }}</p>
      } @else if (error()) {
        <div class="ig-alert ig-alert--error" role="alert">{{ error() }}</div>
      } @else if (page() && page()!.items.length === 0) {
        <div class="ig-empty">
          <div class="ig-empty__mark" aria-hidden="true"></div>
          <p>{{ 'history.empty' | translate }}</p>
          <div class="ig-empty__actions">
            <a routerLink="/search" class="ig-btn ig-btn--primary">{{ 'history.runFirst' | translate }}</a>
          </div>
        </div>
      } @else if (page()) {
        <ul class="ig-hist">
          @for (item of page()!.items; track item.id; let i = $index) {
            <li>
              <a [class]="'ig-hist__row reveal d' + delay(i)" [routerLink]="['/history', item.id]">
                <span class="ig-hist__amount">
                  {{ item.input.amount / 100 | number: '1.2-2' }}
                  <span class="ig-hist__cur">{{ item.input.currency }}</span>
                </span>
                <span class="ig-hist__meta">
                  <span class="ig-hist__date">{{ item.createdAt | date: 'medium' }}</span>
                  <span class="ig-chip" [attr.data-status]="item.status">{{ statusKey(item.status) | translate }}</span>
                  <span class="ig-hist__count">{{ item.optionCount | igPlural: 'option' }}</span>
                </span>
                <svg class="ig-hist__chev" width="16" height="16" viewBox="0 0 16 16" fill="none"
                     stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><path d="M6 3l5 5-5 5"/></svg>
              </a>
            </li>
          }
        </ul>

        <nav class="ig-pager" [attr.aria-label]="'history.paginationLabel' | translate">
          <button type="button" class="ig-btn ig-btn--ghost"
                  [disabled]="page()!.page === 0" (click)="go(page()!.page - 1)">
            {{ 'history.previous' | translate }}
          </button>
          <span class="ig-pager__ind">{{ 'history.pageOf' | translate: { current: page()!.page + 1, total: Math.max(page()!.totalPages, 1) } }}</span>
          <button type="button" class="ig-btn ig-btn--ghost"
                  [disabled]="page()!.page + 1 >= page()!.totalPages" (click)="go(page()!.page + 1)">
            {{ 'history.next' | translate }}
          </button>
        </nav>
      }
    </section>
  `,
  styles: [
    `
      .ig-hist { list-style: none; padding: 0; margin: 1.25rem 0 0; display: grid; gap: .6rem; }
      .ig-hist__row {
        position: relative; overflow: hidden; display: flex; align-items: center; gap: 1rem;
        padding: .9rem 1rem .9rem 1.25rem; border: 1px solid var(--line); border-radius: var(--radius-sm);
        background: var(--surface); color: var(--ink); text-decoration: none;
        transition: border-color .2s var(--ease), box-shadow .2s var(--ease);
      }
      .ig-hist__row::before {
        content: ""; position: absolute; left: 0; top: 0; bottom: 0; width: 4px;
        background: linear-gradient(180deg, var(--blue-600), var(--gold-500));
        transform: scaleY(.3); transition: transform .25s var(--ease);
      }
      .ig-hist__row:hover, .ig-hist__row:focus-visible {
        border-color: var(--line-2); box-shadow: var(--shadow-sm); text-decoration: none;
      }
      .ig-hist__row:hover::before, .ig-hist__row:focus-visible::before { transform: scaleY(1); }
      .ig-hist__amount { font-family: var(--font-mono); font-weight: 700; font-size: 1.05rem; }
      .ig-hist__cur { font-size: .76rem; color: var(--muted); text-transform: uppercase; letter-spacing: .04em; }
      .ig-hist__meta { display: flex; align-items: center; gap: .75rem; margin-left: auto; flex-wrap: wrap; }
      .ig-hist__date { font-size: .82rem; color: var(--muted); }
      .ig-hist__count { font-size: .82rem; color: var(--muted); }
      .ig-hist__chev { flex: none; color: var(--muted); }
      .ig-pager { display: flex; align-items: center; justify-content: center; gap: 1.25rem;
        margin-top: 1.25rem; padding-top: 1rem; border-top: 1px solid var(--line); }
      .ig-pager__ind { font-family: var(--font-mono); font-size: .82rem; color: var(--muted); }
      @media (max-width: 560px) {
        .ig-hist__row { flex-wrap: wrap; gap: .4rem; }
        .ig-hist__meta { margin-left: 0; width: 100%; }
        .ig-hist__chev { display: none; }
      }
      @media (prefers-reduced-motion: reduce) {
        .ig-hist__row::before { transition: none; transform: scaleY(1); }
      }
    `,
  ],
})
export class HistoryComponent implements OnInit {
  private readonly investments = inject(InvestmentService);
  private readonly translate = inject(TranslateService);
  protected readonly Math = Math;
  private static readonly PAGE_SIZE = 10;

  /** Maps a backend status to its translation key, e.g. 'pending' -> 'history.statusPending'. */
  statusKey(status: string): string {
    return 'history.status' + status.charAt(0).toUpperCase() + status.slice(1);
  }

  /** Staggered reveal delay class index, capped at 5 (matches global .reveal .d1..d5). */
  delay(i: number): number {
    return Math.min(i + 1, 5);
  }

  readonly page = signal<HistoryPage | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.go(0);
  }

  go(page: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.investments.history(page, HistoryComponent.PAGE_SIZE).subscribe({
      next: (res) => {
        this.page.set(res);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.translate.instant('history.loadError'));
        this.loading.set(false);
      },
    });
  }
}
