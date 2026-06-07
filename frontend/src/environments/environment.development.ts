/**
 * Development environment (used by `ng serve` / `--configuration development`). The dev server
 * proxies /api to the backend (see proxy.conf.json), so the same relative base path works.
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  /** Canonical site origin used for SEO canonical/hreflang/OG URLs (feature 006). */
  siteOrigin: 'http://localhost:4200',
  /** Cloudflare Web Analytics token (010). Empty in dev so no beacon loads locally. */
  cloudflareAnalyticsToken: '',
  /** Organization sameAs profile URLs (010). Empty in dev. */
  orgSameAs: [] as string[],
};
