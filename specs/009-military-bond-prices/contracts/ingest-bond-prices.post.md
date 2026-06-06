# Contract: POST /api/v1/admin/bond-prices (machine-to-machine ingest)

Shared-secret ingest of a batch of parsed bond prices. Used by the scraper only. Not a user endpoint.

## Auth

- Header `X-Bond-Ingest-Secret: <secret>` (constant-time compared to backend `BOND_INGEST_SECRET`
  via `MessageDigest.isEqual` on raw bytes; the length-mismatch path must not short-circuit early).
- NOT JWT. The route MUST be added explicitly to `SecurityConfig`'s `PUBLIC_POST` allowlist (exact
  path, `POST` only) so the JWT chain does not 401 a request that carries no Bearer token; the
  controller then self-guards on the secret.
- The secret check is a **helper invoked from the controller** (not a servlet filter), so a failure
  throws `ApiException(UNAUTHORIZED)` and `GlobalExceptionHandler` formats the standard
  `ErrorResponse` envelope (a filter would have to write the envelope itself).
- `BOND_INGEST_SECRET` is configured as a **blank-tolerant** `String` in `BondsProperties` (NOT
  `@NotBlank`), so an unset secret does NOT fail app startup — it fails *ingest* closed: when blank,
  ALL ingest is rejected (`401`). `BondsProperties` must be added to the app's
  `@EnableConfigurationProperties` list (this project uses an explicit allowlist, not a scan).
- This is a server-to-server call (GitHub runner / local Node), not a browser, so CORS does not
  apply — the single-origin CORS lock is irrelevant to the scraper.

## Request

- `Content-Type: application/json`
- Body: JSON array of bond objects.

```json
[
  {
    "isin": "UA4000227545",
    "military": true,
    "currency": "UAH",
    "maturity": "2026-11-18",
    "quotationDate": "2026-06-05",
    "sellPriceMinor": 107658,
    "buyPriceMinor": 106900,
    "sellYield": 15.25,
    "buyYield": 15.80
  }
]
```

Field rules: see [data-model.md](../data-model.md) `IngestBondRequest`. `sellPriceMinor`/
`buyPriceMinor` are integer minor units of `currency` (no float). Unknown fields are rejected.

## Responses

| Status | When | Body |
|--------|------|------|
| `200 OK` | Valid secret; batch processed (some records may be individually rejected). | `{ "accepted": 12, "rejected": 0 }` |
| `400 Bad Request` | Malformed body (not an array / unparseable), an **unknown field** on any object (parse-time, whole batch), or an **empty** batch. | Standard `ErrorResponse` (`VALIDATION_ERROR`). |
| `401 Unauthorized` | Missing/incorrect `X-Bond-Ingest-Secret`, or backend secret unset/blank. | Standard `ErrorResponse` (`UNAUTHORIZED`). |

Notes:
- The secret check runs **first**; a bad/missing secret 401s before the body is processed.
- Individual invalid records inside an otherwise-valid, non-empty batch are dropped via the
  service's programmatic per-record validation (see data-model.md) and counted in `rejected`; the
  request still returns `200` with the accepted/rejected tally (FR-010). Note the asymmetry: a
  *structural* problem (non-array, unknown field) 400s the whole batch at parse time, whereas a
  *semantic* per-record problem (bad date, negative price, missing field) drops just that record.
- An empty array is a no-op that does not modify stored data; treated as `400` so a stray empty POST
  cannot be mistaken for success and can never blank the collection (R7 / SC-006).
- Upsert-by-ISIN: existing instruments are updated in place; absent instruments are left untouched
  (no deletes). `fetchedAt` is stamped server-side at ingest.

## Idempotency

Re-POSTing the same batch yields the same stored state (upsert), with `fetchedAt` refreshed. Repeated
runs never create duplicates (one document per ISIN).
