# Contract: Ingest metal prices (machine-to-machine)

`POST /api/v1/admin/metal-prices`

Distinct from the bond ingest channel (FR-008). Bypasses the JWT chain (added to
`SecurityConfig.PUBLIC_POST`); self-guarded by the shared secret only.

## Request

- Header: `X-Metal-Ingest-Secret: <shared secret>` (required).
- Header: `Content-Type: application/json`.
- Body: JSON array of `IngestMetalRequest`:

```json
[
  {
    "metal": "GOLD",
    "rateGroup": "one",
    "weightKey": "1",
    "weightGrams": 1,
    "currency": "UAH",
    "quotationDate": "2026-06-08",
    "purchaseRateMinor": 678000,
    "saleRateMinor": 888000
  },
  {
    "metal": "SILVER",
    "rateGroup": "one",
    "weightKey": "10",
    "weightGrams": 10,
    "currency": "UAH",
    "quotationDate": "2026-06-08",
    "purchaseRateMinor": 10075,
    "saleRateMinor": 28790
  }
]
```

`purchaseRateMinor`/`saleRateMinor` are integer minor units (kopiykas) per gram; the scraper converts
source strings like `"6 780.00"` -> `678000` (whitespace-stripped, BigInt, no float).

## Responses

| Status | Condition | Body |
|--------|-----------|------|
| 200 | Valid secret; batch processed | `{ "accepted": <int>, "rejected": <int> }` |
| 401 | Missing / wrong secret, or server secret blank (fail-closed) | error envelope; nothing stored |
| 400 | Body not a JSON array / empty array / unknown field | error envelope; nothing stored |

Per-record: malformed records are dropped and counted in `rejected`; valid records in the same batch
are still stored (FR-010). Upsert by composite `_id` (one record per metal+rateGroup+weight). No
deletes - records absent from the batch are left untouched.

## Behavior notes

- Auth verified before body parsing; constant-time secret comparison; secret never logged.
- `fetchedAt` is stamped server-side once per batch.
