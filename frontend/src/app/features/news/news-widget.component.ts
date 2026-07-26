import { isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  PLATFORM_ID,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { NewsService } from './news.service';
import { NewsItem } from './news.models';
import { TimeAgo, timeAgo } from './news-time.util';

interface NewsView extends NewsItem {
  ago: TimeAgo;
}

const SHOWN_PER_LIST = 5;

/**
 * Financial news widget (feature 016, FR-5). Self-contained: fetches on init (browser only - the
 * landing page is prerendered and must not fetch at build time), and on ANY failure or a fully
 * empty payload renders nothing at all. An engagement feature must never show a broken card.
 * Headlines stay in their source language by design; only chrome (title, section labels, relative
 * times) is translated.
 */
@Component({
  selector: 'ig-news-widget',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslateModule],
  template: `
    @if (state() === 'loading') {
      <section class="ig-news" aria-hidden="true">
        <div class="ig-news__skeleton"></div>
        <div class="ig-news__skeleton"></div>
      </section>
    } @else if (state() === 'ready') {
      <section class="ig-news" aria-labelledby="ig-news-title">
        <p class="ig-kicker">{{ 'news.title' | translate }}</p>
        <h2 id="ig-news-title" class="ig-sr-only">{{ 'news.title' | translate }}</h2>
        <div class="ig-news__cols">
          <div class="ig-news__col">
            <h3 class="ig-news__label">{{ 'news.ukraine' | translate }}</h3>
            <ul class="ig-news__list">
              @for (n of ukraine(); track n.link) {
                <li class="ig-news__item">
                  <a [href]="n.link" target="_blank" rel="noopener" class="ig-news__link">{{ n.title }}</a>
                  <span class="ig-news__meta ig-muted">
                    {{ n.sourceName }} &middot; {{ n.ago.key | translate: n.ago.params }}
                  </span>
                </li>
              }
            </ul>
          </div>
          <div class="ig-news__col">
            <h3 class="ig-news__label">{{ 'news.world' | translate }}</h3>
            <ul class="ig-news__list">
              @for (n of world(); track n.link) {
                <li class="ig-news__item">
                  <a [href]="n.link" target="_blank" rel="noopener" class="ig-news__link">{{ n.title }}</a>
                  <span class="ig-news__meta ig-muted">
                    {{ n.sourceName }} &middot; {{ n.ago.key | translate: n.ago.params }}
                  </span>
                </li>
              }
            </ul>
          </div>
        </div>
      </section>
    }
  `,
  styles: [
    `
      .ig-news { padding: 2rem 0 0; }
      .ig-news__cols { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
      .ig-news__label { margin: 0 0 0.75rem; font-size: 1rem; }
      .ig-news__list { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.75rem; }
      .ig-news__item { display: grid; gap: 0.15rem; }
      .ig-news__link { text-decoration: none; color: inherit; font-weight: 600; }
      .ig-news__link:hover { text-decoration: underline; }
      .ig-news__meta { font-size: 0.85rem; }
      .ig-news__skeleton {
        height: 1rem; margin: 0.5rem 0; border-radius: 4px;
        background: color-mix(in srgb, currentColor 12%, transparent);
      }
      @media (max-width: 640px) { .ig-news__cols { grid-template-columns: 1fr; } }
    `,
  ],
})
export class NewsWidgetComponent implements OnInit {
  private readonly newsService = inject(NewsService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<'loading' | 'ready' | 'hidden'>('loading');
  readonly ukraine = signal<NewsView[]>([]);
  readonly world = signal<NewsView[]>([]);

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      this.state.set('hidden'); // prerender: no network, no markup, no layout shift above the fold
      return;
    }
    this.newsService
      .latest()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          const now = new Date();
          const view = (items: NewsItem[]): NewsView[] =>
            items.slice(0, SHOWN_PER_LIST).map((i) => ({ ...i, ago: timeAgo(i.publishedAt, now) }));
          const ukraine = view(res.ukraine);
          const world = view(res.world);
          if (ukraine.length === 0 && world.length === 0) {
            this.state.set('hidden');
            return;
          }
          this.ukraine.set(ukraine);
          this.world.set(world);
          this.state.set('ready');
        },
        error: () => this.state.set('hidden'),
      });
  }
}
