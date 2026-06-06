// Warmup + single POST helper (feature 009, research R9/R12).
//
// The production backend (Fly.io) scales to zero, so a cold start can take ~20-35s. A bare retried
// POST is a poor wake probe (it may time out mid-boot). Instead we WARM UP with GET /api/v1/ping
// (a public, cheap route) using backoff until it returns 200, then POST the batch ONCE. The shared
// secret travels only in the X-Bond-Ingest-Secret header and is never logged.

const PING_PATH = '/api/v1/ping';
const INGEST_PATH = '/api/v1/admin/bond-prices';

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchWithTimeout(url, options, timeoutMs) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Poll GET {baseUrl}/api/v1/ping until it responds 200 (waking a scaled-to-zero backend).
 * Throws if it never wakes within the attempt budget.
 */
// Worst-case warmup budget is bounded to sit well under the workflow's 10-min job cap:
// 6 attempts x 45s + linear backoff (3+6+9+12+15 = 45s) ~= 315s, leaving room for nav+XHR (<=120s).
export async function warmup(baseUrl, { attempts = 6, perRequestTimeoutMs = 45000, backoffMs = 3000 } = {}) {
  let lastErr;
  for (let i = 1; i <= attempts; i++) {
    try {
      const res = await fetchWithTimeout(`${baseUrl}${PING_PATH}`, { method: 'GET' }, perRequestTimeoutMs);
      if (res.ok) {
        return;
      }
      lastErr = new Error(`ping returned HTTP ${res.status}`);
    } catch (err) {
      lastErr = err;
    }
    if (i < attempts) {
      await sleep(backoffMs * i); // linear backoff: 3s, 6s, 9s, ...
    }
  }
  throw new Error(`backend did not wake after ${attempts} ping attempts: ${lastErr?.message ?? 'unknown'}`);
}

/**
 * Warm up the backend, then POST the batch once with the shared secret.
 * @returns {Promise<{accepted:number, rejected:number}>}
 */
export async function warmupAndPost(baseUrl, secret, batch, opts = {}) {
  if (!secret) {
    throw new Error('BOND_INGEST_SECRET is not set; refusing to POST');
  }
  await warmup(baseUrl, opts);

  const res = await fetchWithTimeout(`${baseUrl}${INGEST_PATH}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Bond-Ingest-Secret': secret,
    },
    body: JSON.stringify(batch),
  }, opts.perRequestTimeoutMs ?? 45000);

  const text = await res.text();
  if (!res.ok) {
    // Do not echo the secret or headers; the response body is backend-generated and safe to show.
    throw new Error(`ingest POST failed: HTTP ${res.status} ${text}`);
  }
  return JSON.parse(text);
}
