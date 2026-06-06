// Bond price scraper (feature 009).
//
// Loads next.privat24.ua/bonds in a real headless Chromium, lets PrivatBank's own SPA complete its
// cross-domain anti-bot (xref) handshake, and intercepts the POST /api/p24/pub/bonds XHR response
// JSON directly (we never mint the xref ourselves). Each record is converted to the ingest shape
// (prices -> integer minor units), validated (min-record floor + schema), then POSTed to the backend.
//
// FAIL LOUD: on any failure (navigation/XHR timeout, empty/truncated result, validation, POST error)
// this exits non-zero and POSTs NOTHING, so existing stored prices are preserved (SC-006).
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

const BONDS_URL = 'https://next.privat24.ua/bonds';
const XHR_URL_FRAGMENT = '/api/p24/pub/bonds';
const NAV_TIMEOUT_MS = 60000;
const XHR_TIMEOUT_MS = 60000;

const backendBaseUrl = (process.env.BACKEND_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const secret = process.env.BOND_INGEST_SECRET || '';
const minRecords = process.env.BONDS_MIN_RECORDS ? Number(process.env.BONDS_MIN_RECORDS) : DEFAULT_MIN_RECORDS;
const dryRun = String(process.env.BONDS_DRY_RUN || '').toLowerCase() === 'true';

/**
 * Map one raw PrivatBank record to the ingest shape. Prices are quoted per 1000 face value as
 * decimals; we convert to integer minor units. Field names follow the public bonds payload
 * (military, isin, currency, maturity, quotationDate, sellPrice/buyPrice, sellYield/buyYield).
 */
function toIngestRecord(raw) {
  return {
    isin: String(raw.isin || '').trim(),
    military: Boolean(raw.military),
    currency: String(raw.currency || '').trim(),
    maturity: normalizeDate(raw.maturity),
    quotationDate: normalizeDate(raw.quotationDate),
    sellPriceMinor: toMinorUnits(raw.sellPrice),
    buyPriceMinor: toMinorUnits(raw.buyPrice),
    sellYield: Number(raw.sellYield),
    buyYield: Number(raw.buyYield),
  };
}

/** Accept ISO yyyy-MM-dd as-is; trim a datetime to its date part if present. */
function normalizeDate(value) {
  const s = String(value || '').trim();
  return s.length >= 10 ? s.slice(0, 10) : s;
}

async function interceptBonds() {
  const browser = await chromium.launch({ headless: true });
  try {
    const context = await browser.newContext();
    const page = await context.newPage();

    const responsePromise = page.waitForResponse(
      (res) => res.url().includes(XHR_URL_FRAGMENT) && res.request().method() === 'POST' && res.ok(),
      { timeout: XHR_TIMEOUT_MS },
    );

    await page.goto(BONDS_URL, { waitUntil: 'domcontentloaded', timeout: NAV_TIMEOUT_MS });

    const response = await responsePromise;
    const json = await response.json();
    // The payload is expected to be an array (or wrap one); be defensive about a wrapper.
    const records = Array.isArray(json) ? json : (json.bonds || json.data || json.records);
    if (!Array.isArray(records)) {
      throw new Error('intercepted bonds payload was not an array');
    }
    return records;
  } finally {
    await browser.close();
  }
}

async function main() {
  console.log(`[bonds] scraping ${BONDS_URL} -> ${backendBaseUrl} (minRecords=${minRecords}, dryRun=${dryRun})`);

  const rawRecords = await interceptBonds();
  console.log(`[bonds] intercepted ${rawRecords.length} raw record(s)`);

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
