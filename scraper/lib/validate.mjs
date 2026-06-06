// Client-side validation of the parsed/converted bond batch before POST (feature 009, research R11).
//
// A run must FAIL LOUD and POST NOTHING unless the result is a non-empty array that meets a minimum
// record floor (rejects a "clearly truncated" feed) and every record matches the ingest shape
// (contracts/scraper-output.schema.json). The backend re-validates independently (defence in depth).

export const DEFAULT_MIN_RECORDS = 5; // below this we treat the scrape as truncated/failed
const CURRENCIES = new Set(['UAH', 'USD', 'EUR']);
const ISIN_RE = /^[A-Z0-9]{12}$/;
const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function isInteger(n) {
  return typeof n === 'number' && Number.isInteger(n);
}

/** Throws Error describing the first problem with a single record. */
export function validateRecord(r, index) {
  const where = `record[${index}]`;
  if (typeof r !== 'object' || r === null) throw new Error(`${where} is not an object`);
  if (!ISIN_RE.test(r.isin ?? '')) throw new Error(`${where}.isin invalid: ${JSON.stringify(r.isin)}`);
  if (typeof r.military !== 'boolean') throw new Error(`${where}.military must be boolean`);
  if (!CURRENCIES.has(r.currency)) throw new Error(`${where}.currency invalid: ${JSON.stringify(r.currency)}`);
  if (!ISO_DATE_RE.test(r.maturity ?? '')) throw new Error(`${where}.maturity not ISO date`);
  if (!ISO_DATE_RE.test(r.quotationDate ?? '')) throw new Error(`${where}.quotationDate not ISO date`);
  if (!isInteger(r.sellPriceMinor) || r.sellPriceMinor < 0) throw new Error(`${where}.sellPriceMinor must be a non-negative integer`);
  if (!isInteger(r.buyPriceMinor) || r.buyPriceMinor < 0) throw new Error(`${where}.buyPriceMinor must be a non-negative integer`);
  if (!Number.isFinite(r.sellYield)) throw new Error(`${where}.sellYield must be a finite number`);
  if (!Number.isFinite(r.buyYield)) throw new Error(`${where}.buyYield must be a finite number`);
}

/**
 * Validate the whole batch. Throws on any problem (caller must then exit non-zero and not POST).
 * @param {unknown} records
 * @param {{minRecords?: number}} [opts]
 * @returns {Array} the validated records
 */
export function validateBatch(records, opts = {}) {
  const minRecords = opts.minRecords ?? DEFAULT_MIN_RECORDS;
  if (!Array.isArray(records)) throw new Error('scrape result is not an array');
  if (records.length === 0) throw new Error('scrape result is empty');
  if (records.length < minRecords) {
    throw new Error(`scrape result below minimum-record floor (${records.length} < ${minRecords}); treating as truncated`);
  }
  records.forEach(validateRecord);
  return records;
}
