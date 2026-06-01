/**
 * Production environment (default build). No secrets ever live here or anywhere in the bundle
 * (SPECIFICATION §10, AC #10) - only the public API base path. The SPA reaches the backend through
 * the same-origin reverse proxy at /api/v1 (see nginx.conf), so no absolute host is needed.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'https://api.investguideua.com/api/v1',
};
