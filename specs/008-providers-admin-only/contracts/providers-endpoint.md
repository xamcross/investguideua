# Contract: GET /api/v1/providers (ADMIN-only)

**Change**: This endpoint moves from "any authenticated user" to "authenticated **and** holds the
ADMIN role". No request/response body change — only the authorization precondition changes.

## Request

```
GET /api/v1/providers
Authorization: Bearer <access token>
```

No query parameters, no body.

## Authorization rules

| Caller state | Authority required | Outcome | Status |
|--------------|--------------------|---------|--------|
| Valid token, roles include `ADMIN` | `ROLE_ADMIN` | Active provider catalog returned | `200 OK` |
| Valid token, roles = `["USER"]` (or any set without `ADMIN`) | — | Access denied, no catalog data | `403 Forbidden` |
| No / invalid / expired token | — | Authentication required | `401 Unauthorized` |

The `403`/`401` bodies use the existing error envelope produced by `RestAuthEntryPoints`
(`accessDeniedHandler` / `authenticationEntryPoint`).

## Success response (200) — unchanged shape

`ProviderResponse[]`, active providers only (`findByActiveTrue()`), e.g.:

```json
[
  {
    "id": "privatbank",
    "name": "...",
    "category": "GOV_BOND",
    "currencies": ["UAH", "USD"],
    "minAmount": 100000,
    "returnRange": { "min": 13.0, "max": 16.5 },
    "riskLevel": "LOW",
    "sourceUrl": "https://privatbank.ua/ovdp"
  }
]
```

## Acceptance tests (backend)

A MockMvc test exercising the real `SecurityFilterChain` (`spring-security-test` available):

1. `@WithMockUser(roles = "ADMIN")` -> `GET /api/v1/providers` returns `200` and the catalog.
2. `@WithMockUser(roles = "USER")` -> returns `403` and no catalog data.
3. Unauthenticated (no user) -> returns `401`.

These map to spec FR-001, FR-002, FR-003, FR-009 and SC-002, SC-003.
