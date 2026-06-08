// Precious metals price scraper (feature 011).
//
// Unlike the bond scraper, the metals source is a PLAIN PUBLIC GET with no anti-bot handshake, so this
// needs only `fetch` - no headless browser:
//
//     GET https://privatbank.ua/pb/ajax/bank-metall-courses
//
// Response shape:
//   { "status": true, "metalRates": {
//       "gold":   { "rates": { "one"|"two"|"three": { "<weightKey>": { "size": "<weightKey>",
//                   "prices": { "purchaseRate": "6 780.00", "saleRate": "8 880.00" } } } },
//                   "date": "DD.MM.YYYY" },
//       "silver": { ... } } }
//
// We flatten gold+silver x rateGroup x weight into one record each, converting price strings (which
// may contain a space thousands separator) to integer minor units (kopiykas) per gram and the per-metal
// date to ISO. weightKey is preserved verbatim (the composite key on the backend); weightGrams is its
// numeric form for smallest-tier selection.
//
// FAIL LOUD: on any failure (non-200, status!=true, empty/truncated/partial data, validation, POST
// error) this exits non-zero and POSTs NOTHING, so stored prices are preserved.
//
// Env:
//   BACKEND_BASE_URL    default http://localhost:8080 (prod: https://api.investguideua.com)
//   METAL_INGEST_SECRET shared secret for the ingest endpoint (required to POST), DISTINCT from bonds
//   METALS_MIN_RECORDS  optional override of the minimum-record floor
//   METALS_DRY_RUN      "true" to fetch+validate+print without POSTing (local debugging)

import { toMinorUnits } from './lib/convert.mjs';
import { toIsoDate } from './lib/date.mjs';
import { validateMetalBatch, DEFAULT_METALS_MIN_RECORDS } from './lib/validate.mjs';
import { warmupAndPost } from './lib/post.mjs';

const METALS_API_URL = 'https://privatbank.ua/pb/ajax/bank-metall-courses';
const INGEST_PATH = '/api/v1/admin/metal-prices';
const INGEST_HEADER = 'X-Metal-Ingest-Secret';
const SECRET_ENV = 'METAL_INGEST_SECRET';
// Source metal keys (lowercase) mapped to the stored uppercase enum value.
const METAL_KEYS = { gold: 'GOLD', silver: 'SILVER' };

const backendBaseUrl = (process.env.BACKEND_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const secret = process.env.METAL_INGEST_SECRET || '';
const minRecords = process.env.METALS_MIN_RECORDS
  ? Number(process.env.METALS_MIN_RECORDS)
  : DEFAULT_METALS_MIN_RECORDS;
const dryRun = String(process.env.METALS_DRY_RUN || '').toLowerCase() === 'true';

/** GET the metals endpoint and return its metalRates object (throws on any failure). */
async function fetchMetalRates() {
  const res = await fetch(METALS_API_URL, { headers: { Accept: 'application/json' } });
  if (!res.ok) {
    throw new Error(`metals endpoint returned HTTP ${res.status}`);
  }
  let json;
  try {
    json = JSON.parse(await res.text());
  } catch {
    throw new Error('metals response was not valid JSON');
  }
  if (json.status !== true) {
    throw new Error(`metals response status was ${JSON.stringify(json.status)}, not true`);
  }
  if (!json.metalRates || typeof json.metalRates !== 'object') {
    throw new Error('metals payload did not contain a metalRates object');
  }
  return json.metalRates;
}

/** Flatten metalRates into ingest records. Unparseable single entries are skipped (counted). */
function toBatch(metalRates) {
  const batch = [];
  let skipped = 0;
  for (const [srcKey, metal] of Object.entries(METAL_KEYS)) {
    const node = metalRates[srcKey];
    if (!node || !node.rates || typeof node.rates !== 'object') {
      continue; // a missing metal is caught by validateMetalBatch (both metals required)
    }
    const quotationDate = toIsoDate(node.date);
    for (const [rateGroup, weights] of Object.entries(node.rates)) {
      if (!weights || typeof weights !== 'object') continue;
      for (const [weightKey, entry] of Object.entries(weights)) {
        try {
          const prices = (entry && entry.prices) || {};
          const weightGrams = Number(weightKey);
          if (!Number.isFinite(weightGrams) || weightGrams <= 0) {
            throw new Error(`non-numeric weightKey ${JSON.stringify(weightKey)}`);
          }
          batch.push({
            metal,
            rateGroup,
            weightKey,
            weightGrams,
            currency: 'UAH',
            quotationDate,
            purchaseRateMinor: toMinorUnits(prices.purchaseRate),
            saleRateMinor: toMinorUnits(prices.saleRate),
          });
        } catch (err) {
          skipped++;
          console.warn(`[metals] skipping unparseable entry ${metal}/${rateGroup}/${weightKey}: ${err.message}`);
        }
      }
    }
  }
  if (skipped > 0) {
    console.warn(`[metals] skipped ${skipped} unparseable entr(ies); backend re-validates the rest`);
  }
  return batch;
}

async function main() {
  console.log(`[metals] fetching ${METALS_API_URL} -> ${backendBaseUrl} (minRecords=${minRecords}, dryRun=${dryRun})`);

  const metalRates = await fetchMetalRates();
  const batch = toBatch(metalRates);

  validateMetalBatch(batch, { minRecords }); // empty/truncated/partial/invalid -> throw -> no POST
  console.log(`[metals] validated ${batch.length} record(s)`);

  if (dryRun) {
    console.log('[metals] DRY RUN - not posting. Sample:', JSON.stringify(batch[0]));
    return;
  }

  const result = await warmupAndPost(backendBaseUrl, secret, batch, {
    ingestPath: INGEST_PATH,
    headerName: INGEST_HEADER,
    secretEnvName: SECRET_ENV,
  });
  console.log(`[metals] ingest complete: accepted=${result.accepted} rejected=${result.rejected}`);
}

main().catch((err) => {
  // Fail loud; never partially POST. Do not print env/secret.
  console.error(`[metals] FAILED: ${err.message}`);
  process.exit(1);
});
