import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService } from '../../core/i18n/language.service';
import { ArticleService } from './article.service';
import { EmptyStateComponent } from '../shared/empty-state.component';

/**
 * Articles index (feature 006-seo-optimization, US3). Lists all published articles for the active
 * language with title, summary and a link. Prerendered to static HTML at /articles so crawlers and
 * readers see the full list. Per-page SEO meta is handled centrally by SeoService via the route's
 * `seo.articles.*` keys.
 */
@Component({
  selector: 'ig-articles-index',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule, EmptyStateComponent],
  template: `
    <section class="ig-articles">
      <header class="ig-articles__head">
        <p class="ig-kicker">{{ 'articles.kicker' | translate }}</p>
        <h1>{{ 'articles.indexTitle' | translate }}</h1>
        <p class="ig-lead">{{ 'articles.indexLead' | translate }}</p>
      </header>

      @if (articles().length) {
        <ul class="ig-articles__list">
          @for (a of articles(); track a.slug) {
            <li class="ig-card ig-article-card">
              <a [routerLink]="['/articles', a.slug]" class="ig-article-card__link">
                <h2 class="ig-article-card__title">{{ a.title }}</h2>
                <p class="ig-article-card__summary">{{ a.summary }}</p>
                <span class="ig-article-card__more">{{ 'articles.readMore' | translate }} &rarr;</span>
              </a>
            </li>
          }
        </ul>
      } @else {
        <ig-empty-state [message]="'articles.empty' | translate" />
      }
    </section>
  `,
  styles: [
    `
      .ig-articles { max-width: var(--maxw); margin: 0 auto; padding: 2rem 0 3rem; }
      .ig-articles__head { margin-bottom: 1.5rem; }
      .ig-kicker { color: var(--blue-600); font-weight: 700; text-transform: uppercase; letter-spacing: .05em; font-size: .8rem; }
      .ig-articles__list { list-style: none; padding: 0; display: grid; gap: 1rem; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); }
      .ig-article-card { padding: 0; overflow: hidden; }
      .ig-article-card__link { display: block; padding: 1.25rem; text-decoration: none; color: inherit; height: 100%; }
      .ig-article-card__link:hover { text-decoration: none; }
      .ig-article-card__title { font-family: var(--font-display); font-size: 1.15rem; margin: 0 0 .5rem; color: var(--ink); }
      .ig-article-card__summary { color: var(--muted); font-size: .95rem; line-height: 1.5; margin: 0 0 .75rem; }
      .ig-article-card__more { color: var(--blue-600); font-weight: 600; font-size: .9rem; }
    `,
  ],
})
export class ArticlesIndexComponent {
  private readonly articleService = inject(ArticleService);
  private readonly lang = inject(LanguageService);
  protected readonly articles = computed(() => this.articleService.list(this.lang.current()));
}
