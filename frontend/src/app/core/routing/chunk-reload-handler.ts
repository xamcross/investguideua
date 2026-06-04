import { NavigationError, withNavigationErrorHandler } from '@angular/router';

/**
 * Recovers the app when a lazy route fails to load because its code chunk no longer exists
 * on the server (ticket FE-CORE5).
 *
 * Every route is lazy-loaded via dynamic `import()` (see app.routes.ts), and the bundler bakes a
 * content-hashed filename (e.g. `chunk-T5OTISZ3.js`) into each import. When a new build is deployed,
 * every chunk is re-hashed and the previous files are removed. A browser tab that loaded the app
 * BEFORE the deploy still references the old hashes; the next lazy navigation requests a chunk URL
 * that now 404s, and the dynamic import rejects with "Failed to fetch dynamically imported module".
 * Angular surfaces that as a NavigationError, which otherwise dies silently and leaves the nav broken.
 *
 * Recovery: do a single full-document reload of the attempted URL. That re-fetches index.html, which
 * references the CURRENT chunk hashes, and the navigation then succeeds. A short-lived sessionStorage
 * stamp prevents a reload storm if the reload itself keeps failing (a genuinely broken deploy, an
 * offline client, or a chunk error that is NOT a stale-deploy case) - after one attempt within the
 * cooldown we let the error surface instead of looping.
 */
export const RELOAD_STAMP_KEY = 'ig:chunkReloadAt';
export const RELOAD_COOLDOWN_MS = 10_000;

/** Minimal slice of `window` the recovery logic needs - lets the unit test inject a fake. */
export interface ChunkReloadWindow {
  sessionStorage: Pick<Storage, 'getItem' | 'setItem'>;
  location: { pathname: string; search: string; assign: (url: string) => void };
}

/**
 * Heuristic for "the browser could not fetch/parse a lazy ES module". Message wording varies across
 * engines (Chromium, Firefox, WebKit) and across esbuild/webpack, so we match the known phrasings
 * plus the legacy `ChunkLoadError` name. Kept broad on purpose: a false positive only costs one
 * reload (itself rate-limited), while a miss leaves the user stuck.
 */
export function isChunkLoadError(error: unknown): boolean {
  if (error instanceof Error && error.name === 'ChunkLoadError') {
    return true;
  }
  const message = (error instanceof Error ? error.message : String(error ?? '')).toLowerCase();
  return (
    message.includes('failed to fetch dynamically imported module') ||
    message.includes('error loading dynamically imported module') ||
    message.includes('importing a module script failed') ||
    message.includes('failed to load module script') ||
    message.includes('chunkloaderror')
  );
}

/**
 * Decides whether a navigation failure should trigger a recovery reload and performs it.
 * Returns `true` if a reload was issued. Pure enough to unit-test: all environment access goes
 * through the injected `win` and `now`.
 */
export function recoverFromChunkError(
  event: Pick<NavigationError, 'error' | 'url'>,
  win: ChunkReloadWindow,
  now: number,
): boolean {
  if (!isChunkLoadError(event.error)) {
    return false;
  }

  let lastAttempt = 0;
  try {
    lastAttempt = Number(win.sessionStorage.getItem(RELOAD_STAMP_KEY)) || 0;
  } catch {
    // sessionStorage can throw in privacy/sandboxed contexts; treat as "no prior attempt".
  }

  if (now - lastAttempt < RELOAD_COOLDOWN_MS) {
    // Already reloaded once recently and still failing - stop, so we don't loop forever.
    return false;
  }

  try {
    win.sessionStorage.setItem(RELOAD_STAMP_KEY, String(now));
  } catch {
    // If we cannot persist the stamp the worst case is one extra reload; still better than a
    // permanently broken navigation, so proceed.
  }

  // Reload the *attempted* URL (not the current one): a full document load hits the SPA shell,
  // boots the new build, and re-runs the navigation against valid chunk hashes.
  const target = event.url || win.location.pathname + win.location.search;
  win.location.assign(target);
  return true;
}

/**
 * Router feature for {@link provideRouter}. No-op during SSR/prerender (no `window`); in the browser
 * it reloads once per cooldown to pick up a freshly deployed build.
 */
export function withChunkReloadRecovery() {
  return withNavigationErrorHandler((event: NavigationError) => {
    if (typeof window === 'undefined') {
      return;
    }
    recoverFromChunkError(event, window as unknown as ChunkReloadWindow, Date.now());
  });
}
