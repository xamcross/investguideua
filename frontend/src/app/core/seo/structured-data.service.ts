import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { DEFAULT_OG_IMAGE } from './seo-routes.config';

/** Stable JSON-LD node ids for entity-graph consolidation (010 FR-021, US6). */
const ORG_ID_FRAGMENT = '/#organization';

/** Minimal shape the structured-data service needs from an article. */
export interface ArticleJsonLdInput {
  title: string;
  description: string;
  canonicalUrl: string;
  lang: string;
  datePublished: string;
  dateModified: string;
  image?: string | null;
  faq?: ReadonlyArray<{ q: string; a: string }>;
  breadcrumb?: ReadonlyArray<{ name: string; url: string }>;
}

/**
 * JSON-LD structured-data manager (feature 006-seo-optimization, T043-T046).
 *
 * Writes `<script type="application/ld+json">` blocks into the document head. During prerender they
 * are serialized into the static HTML so crawlers see Organization, WebSite (+ SearchAction),
 * Article, BreadcrumbList and FAQPage. Each managed script carries `data-ld` so it can be replaced
 * on navigation without touching unrelated head content. Per contracts/structured-data.contract.md.
 */
@Injectable({ providedIn: 'root' })
export class StructuredDataService {
  private readonly document = inject(DOCUMENT);
  private readonly origin = environment.siteOrigin.replace(/\/+$/, '');

  /** The stable Organization @id used to consolidate the entity graph (010 FR-021). */
  private get orgId(): string {
    return this.origin + ORG_ID_FRAGMENT;
  }

  /** Site-wide Organization + WebSite (with SearchAction). Safe to call on every navigation. */
  setBase(lang: string): void {
    const org: Record<string, unknown> = {
      '@context': 'https://schema.org',
      '@type': 'Organization',
      '@id': this.orgId,
      name: 'InvestGuideUA',
      url: this.origin + '/',
      logo: {
        '@type': 'ImageObject',
        url: this.abs(DEFAULT_OG_IMAGE),
        width: 2000,
        height: 2000,
      },
    };
    // sameAs official profiles (010 US6). Always emitted as an array per the structured-data
    // contract; empty until official profiles exist (deferred gracefully per spec).
    org['sameAs'] = (environment.orgSameAs ?? []).filter((u) => !!u);
    const website = {
      '@context': 'https://schema.org',
      '@type': 'WebSite',
      name: 'InvestGuideUA',
      url: this.origin + '/',
      inLanguage: lang === 'en' ? 'en' : 'uk',
      publisher: { '@id': this.orgId },
      potentialAction: {
        '@type': 'SearchAction',
        target: `${this.origin}/search?amount={amount}`,
        'query-input': 'required name=amount',
      },
    };
    this.write('base', [org, website]);
  }

  /** Article + BreadcrumbList (+ FAQPage when provided) for an article page. */
  setArticle(a: ArticleJsonLdInput): void {
    const blocks: Record<string, unknown>[] = [];
    blocks.push({
      '@context': 'https://schema.org',
      '@type': 'Article',
      headline: a.title.slice(0, 110),
      description: a.description,
      inLanguage: a.lang === 'en' ? 'en' : 'uk',
      datePublished: a.datePublished,
      dateModified: a.dateModified,
      image: this.abs(a.image ?? DEFAULT_OG_IMAGE),
      // Organization byline (010 clarification: no Person reviewer). Author + publisher reference the
      // canonical Organization entity by @id (set in setBase) rather than repeating the object.
      author: { '@id': this.orgId },
      publisher: { '@id': this.orgId },
      mainEntityOfPage: a.canonicalUrl,
    });
    if (a.breadcrumb?.length) {
      blocks.push({
        '@context': 'https://schema.org',
        '@type': 'BreadcrumbList',
        itemListElement: a.breadcrumb.map((b, i) => ({
          '@type': 'ListItem',
          position: i + 1,
          name: b.name,
          item: b.url,
        })),
      });
    }
    if (a.faq?.length) {
      blocks.push({
        '@context': 'https://schema.org',
        '@type': 'FAQPage',
        mainEntity: a.faq.map((f) => ({
          '@type': 'Question',
          name: f.q,
          acceptedAnswer: { '@type': 'Answer', text: f.a },
        })),
      });
    }
    this.write('article', blocks);
  }

  /** Remove article-specific JSON-LD (e.g. when navigating away to a non-article page). */
  clearArticle(): void {
    this.remove('article');
  }

  private abs(path: string): string {
    return path.startsWith('http') ? path : this.origin + (path.startsWith('/') ? path : '/' + path);
  }

  private write(group: string, blocks: Record<string, unknown>[]): void {
    this.remove(group);
    for (const block of blocks) {
      const script = this.document.createElement('script');
      script.setAttribute('type', 'application/ld+json');
      script.setAttribute('data-ld', group);
      script.textContent = JSON.stringify(block);
      this.document.head.appendChild(script);
    }
  }

  private remove(group: string): void {
    this.document.head
      .querySelectorAll(`script[data-ld="${group}"]`)
      .forEach((el) => el.remove());
  }
}
