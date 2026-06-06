# Contract: GET /api/v1/bond-prices (ADMIN read)

Returns all stored bond price records. Served from MongoDB only; never triggers a live scrape
(FR-014). Gated to the ADMIN role exactly like `GET /api/v1/providers`.

## Auth

- `Authorization: Bearer <accessToken>` with the ADMIN role.
- Configured in `SecurityConfig`: `.requestMatchers(HttpMethod.GET, "/api/v1/bond-prices").hasRole("ADMIN")`.

## Request

- No query parameters in v1 (returns the full set; client filters military-only if desired).

## Responses

| Status | When | Body |
|--------|------|------|
| `200 OK` | Authenticated ADMIN. | JSON array of bond price objects (possibly empty). |
| `401 Unauthorized` | No/invalid access token. | Standard `ErrorResponse` (`UNAUTHORIZED`). |
| `403 Forbidden` | Authenticated but not ADMIN. | Standard `ErrorResponse` (`FORBIDDEN`). |

### 200 body example

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
    "buyYield": 15.80,
    "fetchedAt": "2026-06-06T07:00:12Z"
  }
]
```

- Empty collection -> `200` with `[]` (not an error) (spec User Story 1, scenario 3).
- Prices are integer minor units of each row's `currency`; the client formats for display.
