// Shared date conversion for the scrapers (features 009, 011).
//
// PrivatBank quotes dates as DD.MM.YYYY; the backend ingest expects ISO yyyy-MM-dd. Unrecognised
// values are returned as-is so downstream validation rejects them (never silently coerced).

/** Convert DD.MM.YYYY to ISO yyyy-MM-dd (pass through if already ISO). */
export function toIsoDate(value) {
  const s = String(value || '').trim();
  const dmy = s.match(/^(\d{2})\.(\d{2})\.(\d{4})$/);
  if (dmy) return `${dmy[3]}-${dmy[2]}-${dmy[1]}`;
  if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.slice(0, 10);
  return s; // leave unrecognised values for validation to reject
}
