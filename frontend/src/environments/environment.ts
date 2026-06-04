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
};
