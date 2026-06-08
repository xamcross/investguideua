# Contract: Read metal prices (admin-only)

`GET /api/v1/metal-prices`

Administrator-only direct read, gated in `SecurityConfig` with `.hasRole("ADMIN")` (mirrors
`/api/v1/bond-prices` and `/api/v1/providers`). Serves from stored data only; never triggers live
collection (FR-014).

## Request

- Header: `Authorization: Bearer <access token>` for an ADMIN user.

## Responses

| Status | Condition | Body |
|--------|-----------|------|
| 200 | Caller is ADMIN | JSON array of `MetalPriceResponse` (possibly empty) |
| 403 | Authenticated non-admin | error envelope |
| 401 | Anonymous / no token | error envelope |

```json
[
  {
    "id": "GOLD:one:1",
    "metal": "GOLD",
    "rateGroup": "one",
    "weightKey": "1",
    "weightGrams": 1,
    "currency": "UAH",
    "purchaseRateMinor": 678000,
    "saleRateMinor": 888000,
    "quotationDate": "2026-06-08",
    "fetchedAt": "2026-06-08T07:01:13Z"
  }
]
```

Empty store returns `200 []` (not an error), matching the bond read contract.

This is the **only** externally exposed read of raw metal prices. End users never read this; they only
see PRECIOUS_METALS investment options whose price was grounded server-side (FR-014a).
