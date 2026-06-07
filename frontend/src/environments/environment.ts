/**
 * Production environment (default build). No secrets ever live here or anywhere in the bundle
 * (SPECIFICATION §10, AC #10) - only the public API base path. The SPA reaches the backend through
 * the same-origin reverse proxy at /api/v1 (see nginx.conf), so no absolute host is needed.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'https://api.investguideua.com/api/v1',
  /**
   * Canonical site origin for SEO canonical/hreflang/Open Graph absolute URLs (feature 006).
   * One canonical host over HTTPS; the non-canonical host (apex vs www) must 301-redirect to this
   * before launch (research R3). Overridable at build time without a code change if needed.
   */
  siteOrigin: 'https://investguideua.com',
  /**
   * Cloudflare Web Analytics beacon token (010-seo-aeo-optimization). Cookieless, no PII, no consent
   * banner. Empty = beacon not injected. Set per build/env; the real token is supplied operationally.
   */
  cloudflareAnalyticsToken: '0514be08308645b4ab4e9d07532fd92a',
  /**
   * Official brand profile URLs for Organization `sameAs` (010, entity presence). Empty by default
   * (deferred gracefully until profiles exist); add e.g. social/Wikidata URLs here.
   */
  orgSameAs: [] as string[],
};
