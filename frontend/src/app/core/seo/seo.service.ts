import { DOCUMENT } from '@angular/common';
import { Injectable, inject, isDevMode } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { NavigationEnd, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { filter } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DEFAULT_OG_IMAGE,
  EN_ENABLED,
  getStaticSeoPage,
  isIndexable,
  normalizePath,
  stripLangPrefix,
} from './seo-routes.config';

/** Resolved per-page SEO inputs the service writes to the document head. */
export interface SeoMeta {
  /** Already-translated title (<= 60 chars). */
  title: string;
  /** Already-translated description (110-160 chars). */
  description: string;
  /** Canonical path WITHOUT language prefix (e.g. '/articles/ovdp'). */
  canonicalPath: string;
  /** Whether the page may be indexed. Non-indexable pages get `noindex,nofollow`. */
  index: boolean;
  /** Page language ('uk' | 'en'). */
  lang: string;
  /** Absolute-path OG image (defaults to the site default). */
  ogImage?: string;
}

/**
 * Central per-page SEO head manager (feature 006-seo-optimization, T012/T021/T037).
 *
 * Owns the meta description, canonical link, hreflang alternates (uk/en/x-default), the robots
 * directive, and Open Graph + Twitter Card tags. The document `<title>` remains owned by
 * {@link TranslatedTitleStrategy} (it shares the same `seo.*.title` i18n keys), so titles localize
 * and update on language switch without double-ownership.
 *
 * `init()` runs on both the browser and the server platform: during prerender the tags are written
 * into the static HTML head (so crawlers and social cards see them); in the browser they update on
 * each navigation and on language change.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly meta = inject(Meta);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);
  private readonly origin = environment.siteOrigin.replace(/\/+$/, '');

  /** Article pages set their own meta via {@link setArticleMeta}; suppresses the auto static handler. */
  private articleOverride = false;

  /** Wire navigation + language-change handlers. Call once at app start (AppComponent). */
  init(): void {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(() => this.handleNavigation());
    // Re-apply on language switch so description/OG follow the active language live.
    this.translate.onLangChange.subscribe(() => this.handleNavigation());
  }

  /** Called by ArticleDetailComponent to apply article-specific meta (overrides static handling). */
  setArticleMeta(meta: SeoMeta): void {
    this.articleOverride = true;
    this.apply(meta);
  }

  private handleNavigation(): void {
    const url = this.router.url;
    const canonical = stripLangPrefix(normalizePath(url));
    const lang = this.currentLang(normalizePath(url));

    // Article detail pages are handled by their component (it has the title/description).
    if (canonical.startsWith('/articles/')) {
      if (this.articleOverride) {
        return;
      }
      // Fallback (component not yet loaded): still set canonical + indexable robots.
    } else {
      this.articleOverride = false;
    }

    const page = getStaticSeoPage(canonical);
    const index = isIndexable(canonical);

    if (!page) {
      // Private/utility or unknown route: ensure it is not indexed, set canonical to self.
      this.apply({
        title: this.document.title,
        description: '',
        canonicalPath: canonical,
        index,
        lang,
      });
      return;
    }

    this.translate.get([page.titleKey, page.descKey]).subscribe((dict: Record<string, string>) => {
      this.apply({
        title: dict[page.titleKey],
        description: dict[page.descKey],
        canonicalPath: canonical,
        index,
        lang,
        ogImage: page.ogImage,
      });
    });
  }

  /** Write all SEO head tags for the given resolved meta. */
  private apply(m: SeoMeta): void {
    this.warnBounds(m);

    // robots
    this.meta.updateTag({
      name: 'robots',
      content: m.index ? 'index,follow' : 'noindex,nofollow',
    });

    // description
    if (m.description) {
      this.meta.updateTag({ name: 'description', content: m.description });
    }

    const canonicalUrl = this.abs(m.lang === 'en' ? '/en' + this.trimRoot(m.canonicalPath) : m.canonicalPath);
    this.setCanonical(canonicalUrl);
    this.setHreflang(m.canonicalPath, m.index);

    // Open Graph + Twitter (only meaningful for indexable pages, but harmless otherwise).
    const img = this.abs(m.ogImage ?? DEFAULT_OG_IMAGE);
    this.meta.updateTag({ property: 'og:title', content: m.title });
    if (m.description) {
      this.meta.updateTag({ property: 'og:description', content: m.description });
    }
    this.meta.updateTag({ property: 'og:type', content: m.canonicalPath.startsWith('/articles/') ? 'article' : 'website' });
    this.meta.updateTag({ property: 'og:url', content: canonicalUrl });
    this.meta.updateTag({ property: 'og:image', content: img });
    this.meta.updateTag({ property: 'og:site_name', content: 'InvestGuideUA' });
    this.meta.updateTag({ property: 'og:locale', content: m.lang === 'en' ? 'en_US' : 'uk_UA' });
    this.meta.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
    this.meta.updateTag({ name: 'twitter:title', content: m.title });
    if (m.description) {
      this.meta.updateTag({ name: 'twitter:description', content: m.description });
    }
    this.meta.updateTag({ name: 'twitter:image', content: img });
  }

  /** uk lives at root; en under /en. */
  private currentLang(path: string): string {
    return path === '/en' || path.startsWith('/en/') ? 'en' : 'uk';
  }

  private trimRoot(path: string): string {
    return path === '/' ? '' : path;
  }

  private abs(path: string): string {
    return this.origin + (path.startsWith('/') ? path : '/' + path);
  }

  private setCanonical(href: string): void {
    let link = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', href);
  }

  /** Emit uk / en / x-default alternates for indexable pages; remove them otherwise. */
  private setHreflang(canonicalPath: string, index: boolean): void {
    this.document.head
      .querySelectorAll('link[rel="alternate"][hreflang]')
      .forEach((el) => el.remove());
    if (!index) {
      return;
    }
    const ukHref = this.abs(canonicalPath);
    const enHref = this.abs('/en' + this.trimRoot(canonicalPath));
    // Only advertise the `en` alternate once the /en mirror is actually published (US4); otherwise
    // it would be an hreflang pointing at a 404. Kept consistent with the sitemap generator.
    const alternates: ReadonlyArray<readonly [string, string]> = EN_ENABLED
      ? [['uk', ukHref], ['en', enHref], ['x-default', ukHref]]
      : [['uk', ukHref], ['x-default', ukHref]];
    for (const [hreflang, href] of alternates) {
      const link = this.document.createElement('link');
      link.setAttribute('rel', 'alternate');
      link.setAttribute('hreflang', hreflang);
      link.setAttribute('href', href);
      this.document.head.appendChild(link);
    }
  }

  private warnBounds(m: SeoMeta): void {
    if (!isDevMode() || !m.index) {
      return;
    }
    if (m.title && m.title.length > 60) {
      console.warn(`[SEO] title exceeds 60 chars (${m.title.length}): "${m.title}"`);
    }
    if (m.description && (m.description.length < 110 || m.description.length > 160)) {
      console.warn(
        `[SEO] description out of 110-160 range (${m.description.length}) for ${m.canonicalPath}`,
      );
    }
  }
}
