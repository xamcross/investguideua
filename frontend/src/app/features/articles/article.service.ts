import { Injectable } from '@angular/core';
import { ARTICLES, Article } from './articles.data';

/**
 * Read-only access to the build-generated article index (feature 006-seo-optimization).
 * Articles are compiled from Markdown at build time (tools/seo/build-articles.mjs); this service
 * just exposes the published set to the components, filtered by language.
 */
@Injectable({ providedIn: 'root' })
export class ArticleService {
  /** All published articles in a language, newest first. Falls back to uk if a language is empty. */
  list(lang: string): readonly Article[] {
    const byLang = ARTICLES.filter((a) => a.lang === lang);
    const items = byLang.length ? byLang : ARTICLES.filter((a) => a.lang === 'uk');
    return [...items].sort((a, b) => b.datePublished.localeCompare(a.datePublished));
  }

  /** A single published article by slug+lang, or the uk version as a fallback, else undefined. */
  bySlug(slug: string, lang: string): Article | undefined {
    return (
      ARTICLES.find((a) => a.slug === slug && a.lang === lang) ??
      ARTICLES.find((a) => a.slug === slug && a.lang === 'uk')
    );
  }

  /** Resolve an article's related items in the same language. */
  related(article: Article): readonly Article[] {
    return article.relatedSlugs
      .map((s) => this.bySlug(s, article.lang))
      .filter((a): a is Article => !!a);
  }
}
