import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { InvestmentService } from '../../core/investment/investment.service';
import { HistoryPage } from '../../core/investment/investment.models';

/**
 * Paginated search history (ticket FE-HIST1, §4.4, §5.1). Newest first, owner-scoped by the backend.
 * Each row links to the detail view. Empty history shows a friendly empty state.
 */
@Component({
  selector: 'ig-history',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, DecimalPipe],
  template: `
    <section class="ig-card">
      <h1>Your search history</h1>

      @if (loading()) {
        <p class="ig-muted">Loading…</p>
      } @else if (error()) {
        <div class="ig-alert ig-alert--error">{{ error() }}</div>
      } @else if (page() && page()!.items.length === 0) {
        <div class="ig-alert ig-alert--info">
          No searches yet. <a routerLink="/search">Run your first search</a>.
        </div>
      } @else if (page()) {
        <ul class="ig-hist">
          @for (item of page()!.items; track item.id) {
            <li class="ig-hist__row">
              <a class="ig-hist__link" [routerLink]="['/history', item.id]">
                <span class="ig-hist__amount">
                  {{ item.input.amount / 100 | number: '1.2-2' }} {{ item.input.currency }}
                </span>
                <span class="ig-hist__meta">
                  {{ item.createdAt | date: 'medium' }} ·
                  <span class="ig-hist__status" [attr.data-status]="item.status">{{ item.status }}</span>
                  · {{ item.optionCount }} {{ item.optionCount === 1 ? 'option' : 'options' }}
                </span>
              </a>
            </li>
          }
        </ul>

        <div class="ig-pager">
          <button type="button" class="ig-btn ig-btn--ghost"
                  [disabled]="page()!.page === 0" (click)="go(page()!.page - 1)">
            Previous
          </button>
          <span class="ig-muted">Page {{ page()!.page + 1 }} of {{ Math.max(page()!.totalPages, 1) }}</span>
          <button type="button" class="ig-btn ig-btn--ghost"
                  [disabled]="page()!.page + 1 >= page()!.totalPages" (click)="go(page()!.page + 1)">
            Next
          </button>
        </div>
      }
    </section>
  `,
  styles: [
    `
      .ig-hist { list-style: none; padding: 0; margin: 0; display: grid; gap: 0.5rem; }
      .ig-hist__link { display: flex; flex-direction: column; gap: 0.2rem; text-decoration: none; padding: 0.7rem 0.9rem; border: 1px solid var(--ig-border); border-radius: 8px; color: var(--ig-ink); }
      .ig-hist__link:hover { border-color: var(--ig-blue); }
      .ig-hist__amount { font-weight: 700; }
      .ig-hist__meta { font-size: 0.82rem; color: var(--ig-muted); }
      .ig-hist__status { text-transform: capitalize; font-weight: 600; }
      .ig-hist__status[data-status='completed'] { color: #1e7a3c; }
      .ig-hist__status[data-status='failed'] { color: #b3261e; }
      .ig-pager { display: flex; align-items: center; gap: 1rem; margin-top: 1rem; }
      .ig-alert--info { background: rgba(0, 87, 183, 0.06); border: 1px solid var(--ig-border); border-radius: 8px; padding: 0.75rem 1rem; }
    `,
  ],
})
export class HistoryComponent implements OnInit {
  private readonly investments = inject(InvestmentService);
  protected readonly Math = Math;
  private static readonly PAGE_SIZE = 10;

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
        this.error.set('Could not load your history. Please try again.');
        this.loading.set(false);
      },
    });
  }
}
