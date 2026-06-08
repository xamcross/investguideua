// Unit tests for warmupAndPost parameterization (features 009, 011). Run: `node --test`.
//
// Guards the 009 regression: the bond defaults (path/header/secret-env) must be unchanged, while the
// metals scraper can override them. fetch is stubbed so no network is touched.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { warmupAndPost } from '../lib/post.mjs';

/** Install a fake global fetch that records calls and returns a canned ingest result. */
function withFakeFetch(run) {
  const calls = [];
  const original = globalThis.fetch;
  globalThis.fetch = async (url, options = {}) => {
    calls.push({ url, options });
    return {
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ accepted: 1, rejected: 0 }),
    };
  };
  return Promise.resolve(run(calls)).finally(() => {
    globalThis.fetch = original;
  });
}

const BASE = 'http://backend.test';

test('bond defaults are unchanged (009 regression)', async () => {
  await withFakeFetch(async (calls) => {
    const result = await warmupAndPost(BASE, 'bond-secret', [{ x: 1 }]);
    assert.deepEqual(result, { accepted: 1, rejected: 0 });

    const ping = calls.find((c) => String(c.url).endsWith('/api/v1/ping'));
    assert.ok(ping, 'warmup should ping /api/v1/ping');

    const post = calls.find((c) => (c.options.method || 'GET') === 'POST');
    assert.ok(post, 'should POST once');
    assert.equal(post.url, `${BASE}/api/v1/admin/bond-prices`);
    assert.equal(post.options.headers['X-Bond-Ingest-Secret'], 'bond-secret');
  });
});

test('metals override targets the metals route and header', async () => {
  await withFakeFetch(async (calls) => {
    await warmupAndPost(BASE, 'metal-secret', [{ x: 1 }], {
      ingestPath: '/api/v1/admin/metal-prices',
      headerName: 'X-Metal-Ingest-Secret',
      secretEnvName: 'METAL_INGEST_SECRET',
    });

    const post = calls.find((c) => (c.options.method || 'GET') === 'POST');
    assert.ok(post, 'should POST once');
    assert.equal(post.url, `${BASE}/api/v1/admin/metal-prices`);
    assert.equal(post.options.headers['X-Metal-Ingest-Secret'], 'metal-secret');
    assert.equal(post.options.headers['X-Bond-Ingest-Secret'], undefined);
  });
});

test('blank secret refuses to POST and names the configured env var', async () => {
  await withFakeFetch(async (calls) => {
    await assert.rejects(
      () => warmupAndPost(BASE, '', [{ x: 1 }], { secretEnvName: 'METAL_INGEST_SECRET' }),
      /METAL_INGEST_SECRET is not set/,
    );
    assert.equal(calls.length, 0, 'must not ping or POST when the secret is blank');
  });
});
