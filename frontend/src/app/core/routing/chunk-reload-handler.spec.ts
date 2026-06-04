import {
  ChunkReloadWindow,
  RELOAD_COOLDOWN_MS,
  RELOAD_STAMP_KEY,
  isChunkLoadError,
  recoverFromChunkError,
} from './chunk-reload-handler';

/**
 * Ticket FE-CORE5: a lazy route whose hashed chunk was removed by a newer deploy must recover by
 * reloading the attempted URL once, without looping when the reload keeps failing.
 */
describe('chunk-reload-handler', () => {
  describe('isChunkLoadError', () => {
    it('matches the cross-engine dynamic-import failure messages', () => {
      expect(isChunkLoadError(new Error('Failed to fetch dynamically imported module: /chunk-A.js'))).toBe(true);
      expect(isChunkLoadError(new Error('error loading dynamically imported module'))).toBe(true);
      expect(isChunkLoadError(new TypeError('Importing a module script failed.'))).toBe(true);
    });

    it('matches a ChunkLoadError by name even with an unusual message', () => {
      const err = new Error('boom');
      err.name = 'ChunkLoadError';
      expect(isChunkLoadError(err)).toBe(true);
    });

    it('ignores unrelated navigation errors (e.g. a guard rejection)', () => {
      expect(isChunkLoadError(new Error('NG04002: redirectTo'))).toBe(false);
      expect(isChunkLoadError(null)).toBe(false);
      expect(isChunkLoadError(undefined)).toBe(false);
    });
  });

  describe('recoverFromChunkError', () => {
    let store: Record<string, string>;
    let assigned: string[];
    let win: ChunkReloadWindow;

    beforeEach(() => {
      store = {};
      assigned = [];
      win = {
        sessionStorage: {
          getItem: (k) => (k in store ? store[k] : null),
          setItem: (k, v) => void (store[k] = v),
        },
        location: { pathname: '/current', search: '?q=1', assign: (u) => assigned.push(u) },
      };
    });

    it('reloads the attempted URL on a chunk error and stamps the attempt', () => {
      const reloaded = recoverFromChunkError(
        { error: new Error('Failed to fetch dynamically imported module'), url: '/tokens' },
        win,
        1_000_000,
      );

      expect(reloaded).toBe(true);
      expect(assigned).toEqual(['/tokens']);
      expect(store[RELOAD_STAMP_KEY]).toBe('1000000');
    });

    it('falls back to the current location when the event has no URL', () => {
      recoverFromChunkError({ error: new Error('importing a module script failed'), url: '' }, win, 1_700_000_000_000);
      expect(assigned).toEqual(['/current?q=1']);
    });

    it('does NOT reload for unrelated navigation errors', () => {
      const reloaded = recoverFromChunkError({ error: new Error('guard blocked'), url: '/tokens' }, win, 1);
      expect(reloaded).toBe(false);
      expect(assigned).toEqual([]);
    });

    it('does not loop: a second failure within the cooldown is ignored', () => {
      const event = { error: new Error('Failed to fetch dynamically imported module'), url: '/tokens' };

      expect(recoverFromChunkError(event, win, 1_000_000)).toBe(true);
      // Reload happened but the chunk is still missing and the nav fails again moments later.
      expect(recoverFromChunkError(event, win, 1_000_000 + RELOAD_COOLDOWN_MS - 1)).toBe(false);
      expect(assigned).toEqual(['/tokens']); // only the first attempt reloaded
    });

    it('reloads again once the cooldown has elapsed (a later, unrelated deploy)', () => {
      const event = { error: new Error('Failed to fetch dynamically imported module'), url: '/tokens' };

      expect(recoverFromChunkError(event, win, 1_000_000)).toBe(true);
      expect(recoverFromChunkError(event, win, 1_000_000 + RELOAD_COOLDOWN_MS)).toBe(true);
      expect(assigned).toEqual(['/tokens', '/tokens']);
    });
  });
});
