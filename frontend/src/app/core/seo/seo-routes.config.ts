/**
 * Central SEO route classification + per-page metadata (feature 006-seo-optimization, T009/T010).
 *
 * Single source of truth shared by the SeoService (runtime/prerender meta), the prerender
 * allow-list, and the sitemap/robots generator. Public pages are explicitly enumerated; everything
 * else is treated as non-indexable (private/utility) and gets a `noindex` robots directive and is
 * excluded from the sitemap.
 *
 * Title and description are ngx-translate KEYS so both languages resolve from the same dictionaries.
 * Length bounds (title <= 60, description 110-160) are enforced on the RESOLVED strings by the
 * SeoService (dev-time warning) and by the build-time SEO audit (hard fail).
 */
export interface SeoPage {
  /** Canonical path WITHOUT language prefix (e.g. '/', '/terms', '/articles'). */
  readonly path: string;
  /** ngx-translate key for the document title (<= 60 chars resolved). */
  readonly titleKey: string;
  /** ngx-translate key for the meta description (110-160 chars resolved). */
  readonly descKey: string;
  /** Per-page Open Graph image (absolute path under the site origin). Defaults to the site default. */
  readonly ogImage?: string;
}

/** The default Open Graph / Twitter share image (served from the site root). */
export const DEFAULT_OG_IMAGE = '/og-default.png';

/**
 * Whether the English (`/en`) mirror is published. While false, hreflang emits only uk +
 * x-default so the site never advertises an `en` alternate that would 404 (US4 enables this).
 * Keep in sync with the prerender allow-list and the sitemap generator.
 */
export const EN_ENABLED = false;

/**
 * All PUBLIC, indexable pages (uk at root; the `/en` mirror is derived by prefixing). Article
 * detail pages are appended at build time from the published-article index (US3), so they are not
 * listed statically here.
 */
export const PUBLIC_PAGES: readonly SeoPage[] = [
  { path: '/', titleKey: 'seo.home.title', descKey: 'seo.home.desc' },
  { path: '/articles', titleKey: 'seo.articles.title', descKey: 'seo.articles.desc' },
  { path: '/terms', titleKey: 'seo.terms.title', descKey: 'seo.terms.desc' },
  { path: '/privacy', titleKey: 'seo.privacy.title', descKey: 'seo.privacy.desc' },
  // Editorial Policy / About + Contact (010-seo-aeo-optimization, E-E-A-T footprint). Mirror these
  // in tools/seo/build-articles.mjs STATIC_PAGES so they prerender and enter the sitemap/manifest.
  { path: '/editorial-policy', titleKey: 'seo.editorial.title', descKey: 'seo.editorial.desc' },
  { path: '/contact', titleKey: 'seo.contact.title', descKey: 'seo.contact.desc' },
];

/**
 * Private/utility path prefixes - never indexed, never in the sitemap, get `noindex`. Matched as
 * prefixes so children (e.g. `/history/:id`, `/payments/result`) are covered.
 */
export const PRIVATE_PREFIXES: readonly string[] = [
  '/login',
  '/register',
  '/verify',
  '/search',
  '/history',
  '/tokens',
  '/payments',
  '/account',
  '/providers',
];

/** Strip a leading `/en` language prefix, returning the canonical (uk) path. */
export function stripLangPrefix(path: string): string {
  if (path === '/en' || path === '/en/') {
    return '/';
  }
  return path.startsWith('/en/') ? path.slice(3) : path;
}

/** Normalize a router URL: drop query/fragment and any trailing slash (except root). */
export function normalizePath(url: string): string {
  const noQuery = url.split('#')[0].split('?')[0];
  if (noQuery.length > 1 && noQuery.endsWith('/')) {
    return noQuery.slice(0, -1);
  }
  return noQuery || '/';
}

/** True if the (language-stripped) path is a public, indexable page. */
export function isIndexable(canonicalPath: string): boolean {
  if (PRIVATE_PREFIXES.some((p) => canonicalPath === p || canonicalPath.startsWith(p + '/'))) {
    return false;
  }
  // Public static pages OR an article detail page (/articles/<slug>).
  return (
    PUBLIC_PAGES.some((pg) => pg.path === canonicalPath) ||
    canonicalPath.startsWith('/articles/')
  );
}

/** Look up the SEO metadata for a static public page by canonical path. */
export function getStaticSeoPage(canonicalPath: string): SeoPage | undefined {
  return PUBLIC_PAGES.find((pg) => pg.path === canonicalPath);
}
