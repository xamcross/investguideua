// Bond price scraper (feature 009).
//
// Loads next.privat24.ua in a real headless Chromium and lets PrivatBank's own SPA complete its
// cross-domain anti-bot handshake (POST /api/p24/init), which mints an anonymous session `xref`.
// We capture that xref and then call the public bonds endpoint from inside the page context
// (same-origin fetch, reusing the SPA's cookies + xref):
//
//     POST /api/p24/pub/bonds  { action: "bargaining", xref }
//
// We do NOT reconstruct the xref ourselves - the browser performs the handshake; we only reuse the
// token it produced. (Navigating straight to /bonds does NOT auto-fire this XHR: an anonymous session
// is redirected to the wallet home, so waiting for the XHR would time out - hence the explicit call.)
//
// The response shape is { status: "success", data: [ {isin, currency, maturity (DD.MM.YYYY),
// quotationDate (DD.MM.YYYY), sellPrice, buyPrice, sellYield, buyYield, military, coupons, ...} ] }.
// Prices are quoted per 1000 face value as decimals; we convert to integer minor units. Dates are
// DD.MM.YYYY and are converted to ISO yyyy-MM-dd.
//
// FAIL LOUD: on any failure (handshake/xref timeout, non-success response, empty/truncated data,
// validation, POST error) this exits non-zero and POSTs NOTHING, so stored prices are preserved.
//
// Env:
//   BACKEND_BASE_URL   default http://localhost:8080 (prod: https://api.investguideua.com)
//   BOND_INGEST_SECRET shared secret for the ingest endpoint (required to POST)
//   BONDS_MIN_RECORDS  optional override of the minimum-record floor
//   BONDS_DRY_RUN      "true" to scrape+validate+print without POSTing (local debugging)

import { chromium } from 'playwright';
import { toMinorUnits } from './lib/convert.mjs';
import { validateBatch, DEFAULT_MIN_RECORDS } from './lib/validate.mjs';
import { warmupAndPost } from './lib/post.mjs';

const SITE_URL = 'https://next.privat24.ua/bonds';
const BONDS_API_PATH = '/api/p24/pub/bonds';
const NAV_TIMEOUT_MS = 60000;
const XREF_WAIT_MS = 30000;
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

const backendBaseUrl = (process.env.BACKEND_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const secret = process.env.BOND_INGEST_SECRET || '';
const minRecords = process.env.BONDS_MIN_RECORDS ? Number(process.env.BONDS_MIN_RECORDS) : DEFAULT_MIN_RECORDS;
const dryRun = String(process.env.BONDS_DRY_RUN || '').toLowerCase() === 'true';

/** Map one raw PrivatBank record to the ingest shape (prices -> integer minor units). */
function toIngestRecord(raw) {
  return {
    isin: String(raw.isin || '').trim(),
    military: Boolean(raw.military),
    currency: String(raw.currency || '').trim(),
    maturity: toIsoDate(raw.maturity),
    quotationDate: toIsoDate(raw.quotationDate),
    sellPriceMinor: toMinorUnits(raw.sellPrice),
    buyPriceMinor: toMinorUnits(raw.buyPrice),
    sellYield: Number(raw.sellYield),
    buyYield: Number(raw.buyYield),
  };
}

/** PrivatBank quotes dates as DD.MM.YYYY; convert to ISO yyyy-MM-dd (pass through if already ISO). */
function toIsoDate(value) {
  const s = String(value || '').trim();
  const dmy = s.match(/^(\d{2})\.(\d{2})\.(\d{4})$/);
  if (dmy) return `${dmy[3]}-${dmy[2]}-${dmy[1]}`;
  if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.slice(0, 10);
  return s; // leave unrecognised values for validation to reject
}

/** Launch the browser, let the SPA mint an xref, then fetch the bonds payload reusing it. */
async function fetchBonds() {
  const browser = await chromium.launch({ headless: true });
  try {
    const context = await browser.newContext({ userAgent: USER_AGENT, locale: 'uk-UA' });
    const page = await context.newPage();

    // Capture the anonymous session xref the SPA mints during its handshake.
    let xref = null;
    page.on('request', (req) => {
      const m = req.url().match(/[?&]xref=([0-9a-f]+)/i);
      if (m && !xref) xref = m[1];
    });

    await page.goto(SITE_URL, { waitUntil: 'domcontentloaded', timeout: NAV_TIMEOUT_MS });

    for (let waited = 0; waited < XREF_WAIT_MS && !xref; waited += 1000) {
      await page.waitForTimeout(1000);
    }
    if (!xref) {
      throw new Error('did not capture an xref from PrivatBank within timeout (handshake failed?)');
    }

    // Call the bonds endpoint from inside the page (same-origin, reuses cookies + xref).
    const payload = await page.evaluate(async ({ path, x }) => {
      const res = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'bargaining', xref: x }),
      });
      const text = await res.text();
      return { status: res.status, text };
    }, { path: BONDS_API_PATH, x: xref });

    if (payload.status !== 200) {
      throw new Error(`bonds endpoint returned HTTP ${payload.status}`);
    }
    let json;
    try {
      json = JSON.parse(payload.text);
    } catch {
      throw new Error('bonds response was not valid JSON');
    }
    if (json.status && json.status !== 'success') {
      throw new Error(`bonds response status was "${json.status}", not "success"`);
    }
    const records = Array.isArray(json) ? json : json.data;
    if (!Array.isArray(records)) {
      throw new Error('bonds payload did not contain a data array');
    }
    return records;
  } finally {
    await browser.close();
  }
}

async function main() {
  console.log(`[bonds] scraping ${SITE_URL} -> ${backendBaseUrl} (minRecords=${minRecords}, dryRun=${dryRun})`);

  const rawRecords = await fetchBonds();
  console.log(`[bonds] received ${rawRecords.length} raw record(s)`);

  // Map per-record so a single malformed record does not kill an otherwise-good refresh; the
  // backend re-validates and drops bad records too. A broadly broken/truncated feed is still caught
  // by the minimum-record floor in validateBatch (which fails loud -> no POST).
  const batch = [];
  let skipped = 0;
  for (const raw of rawRecords) {
    try {
      batch.push(toIngestRecord(raw));
    } catch (err) {
      skipped++;
      console.warn(`[bonds] skipping unparseable record: ${err.message}`);
    }
  }
  if (skipped > 0) {
    console.warn(`[bonds] skipped ${skipped} unparseable record(s); backend will re-validate the rest`);
  }

  validateBatch(batch, { minRecords }); // empty/truncated/invalid -> throw -> no POST
  console.log(`[bonds] validated ${batch.length} record(s) (${batch.filter((b) => b.military).length} military)`);

  if (dryRun) {
    console.log('[bonds] DRY RUN - not posting. Sample:', JSON.stringify(batch[0]));
    return;
  }

  const result = await warmupAndPost(backendBaseUrl, secret, batch);
  console.log(`[bonds] ingest complete: accepted=${result.accepted} rejected=${result.rejected}`);
}

main().catch((err) => {
  // Fail loud; never partially POST. Do not print env/secret.
  console.error(`[bonds] FAILED: ${err.message}`);
  process.exit(1);
});
