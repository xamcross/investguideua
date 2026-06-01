/**
 * Development environment (used by `ng serve` / `--configuration development`). The dev server
 * proxies /api to the backend (see proxy.conf.json), so the same relative base path works.
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
};
