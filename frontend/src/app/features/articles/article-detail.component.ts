import { ChangeDetectionStrategy, Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import { LanguageService } from '../../core/i18n/language.service';
import { SeoService } from '../../core/seo/seo.service';
import { StructuredDataService } from '../../core/seo/structured-data.service';
import { ArticleService } from './article.service';
import { Article } from './articles.data';

/**
 * Article detail page (feature 006-seo-optimization, US3). Renders the prerendered article body,
 * publication + last-updated dates, the mandatory financial disclaimer, related articles, and a CTA
 * into the investment-discovery flow. Applies article-specific SEO meta (title/description/canonical/
 * OG) and JSON-LD (Article + BreadcrumbList + FAQPage), all of which serialize into the prerendered
 * HTML head.
 */
@Component({
  selector: 'ig-article-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule],
  template: `
    @if (article(); as a) {
      <article class="ig-article">
        <nav class="ig-breadcrumb" aria-label="breadcrumb">
          <a routerLink="/">{{ 'nav.home' | translate }}</a> /
          <a routerLink="/articles">{{ 'articles.kicker' | translate }}</a>
        </nav>
        <h1 class="ig-article__title">{{ a.title }}</h1>
        <p class="ig-article__dates">
          <span>{{ 'articles.published' | translate }}: {{ a.datePublished }}</span>
          <span> &middot; {{ 'articles.updated' | translate }}: {{ a.dateModified }}</span>
        </p>

        <div class="ig-article__body" [innerHTML]="a.bodyHtml"></div>

        <p class="ig-article__disclaimer">{{ 'articles.disclaimer' | translate }}</p>

        <div class="ig-article__cta">
          <a routerLink="/search" class="ig-btn ig-btn--gold ig-btn--lg">{{ 'articles.cta' | translate }}</a>
        </div>

        @if (related().length) {
          <aside class="ig-article__related">
            <h2>{{ 'articles.related' | translate }}</h2>
            <ul>
              @for (r of related(); track r.slug) {
                <li><a [routerLink]="['/articles', r.slug]">{{ r.title }}</a></li>
              }
            </ul>
          </aside>
        }
      </article>
    } @else {
      <p class="ig-muted">{{ 'articles.empty' | translate }}</p>
    }
  `,
  styles: [
    `
      .ig-article { max-width: 46rem; margin: 0 auto; padding: 2rem 0 3rem; }
      .ig-breadcrumb { font-size: .85rem; color: var(--muted); margin-bottom: 1rem; }
      .ig-breadcrumb a { color: var(--blue-600); text-decoration: none; }
      .ig-article__title { font-family: var(--font-display); font-size: clamp(1.6rem, 4vw, 2.4rem); line-height: 1.2; margin: 0 0 .5rem; }
      .ig-article__dates { color: var(--muted); font-size: .85rem; margin: 0 0 1.5rem; }
      .ig-article__body { line-height: 1.7; font-size: 1.05rem; }
      .ig-article__body :is(h2, h3) { font-family: var(--font-display); margin-top: 2rem; }
      .ig-article__body a { color: var(--blue-600); }
      .ig-article__disclaimer { margin-top: 2rem; padding: 1rem; border-left: 3px solid var(--gold-500); background: var(--gold-100, #fff8e1); color: var(--muted); font-size: .9rem; border-radius: 6px; }
      .ig-article__cta { margin: 2rem 0; text-align: center; }
      .ig-article__related { margin-top: 2.5rem; border-top: 1px solid var(--line); padding-top: 1.5rem; }
      .ig-article__related ul { list-style: none; padding: 0; display: grid; gap: .5rem; }
      .ig-article__related a { color: var(--blue-600); text-decoration: none; font-weight: 600; }
    `,
  ],
})
export class ArticleDetailComponent implements OnInit {
  /** Route param `:slug` bound via withComponentInputBinding. */
  @Input() slug = '';

  private readonly articleService = inject(ArticleService);
  private readonly lang = inject(LanguageService);
  private readonly seo = inject(SeoService);
  private readonly structuredData = inject(StructuredDataService);
  private readonly translate = inject(TranslateService);
  private readonly titleService = inject(Title);
  private readonly origin = environment.siteOrigin.replace(/\/+$/, '');

  protected readonly article = signal<Article | undefined>(undefined);
  protected readonly related = computed(() => {
    const a = this.article();
    return a ? this.articleService.related(a) : [];
  });

  ngOnInit(): void {
    const lang = this.lang.current();
    const a = this.articleService.bySlug(this.slug, lang);
    this.article.set(a);
    if (!a) {
      return;
    }
    const path = `/articles/${a.slug}`;
    const canonicalUrl = this.origin + (a.lang === 'en' ? '/en' + path : path);

    // The article route has no static title key, so set the document <title> here (works during
    // prerender via the platform-server Title service). Append the brand for consistency.
    this.titleService.setTitle(`${a.title} - InvestGuideUA`);

    this.seo.setArticleMeta({
      title: a.title,
      description: a.description,
      canonicalPath: path,
      index: true,
      lang: a.lang,
      ogImage: a.ogImage ?? undefined,
    });

    const homeName = this.translate.instant('nav.home');
    const articlesName = this.translate.instant('articles.kicker');
    this.structuredData.setArticle({
      title: a.title,
      description: a.description,
      canonicalUrl,
      lang: a.lang,
      datePublished: a.datePublished,
      dateModified: a.dateModified,
      image: a.ogImage,
      faq: a.faq,
      breadcrumb: [
        { name: homeName, url: this.origin + '/' },
        { name: articlesName, url: this.origin + '/articles' },
        { name: a.title, url: canonicalUrl },
      ],
    });
  }
}
