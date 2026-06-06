# Contract: Registration role assignment (USER-only)

**Guarantee**: An account created through the public registration flow is always assigned exactly
`roles = ["USER"]` and can never obtain `ADMIN` via any self-service path. This is already true in
code; the contract pins it against regression.

## Request (unchanged)

```
POST /api/v1/auth/register
Content-Type: application/json

{ "email": "user@example.com", "password": "<>=8 chars, letter+digit>" }
```

The request schema (`RegisterRequest`) contains **only** `email` and `password`. There is no
`roles`/`role` field. Unknown fields are not bound to a role, so a client cannot request elevation.

## Behavior rules

| Rule | Expected |
|------|----------|
| Newly registered account roles | `["USER"]` exactly (no `ADMIN`). |
| Email verification | Does not change `roles`. |
| Login / refresh | Does not change `roles`. |
| Any client-supplied role-like field | Ignored; account is still `["USER"]`. |

## Acceptance tests (backend)

1. After `register(...)`, the persisted user's `roles` equals `["USER"]` and does not contain
   `ADMIN`.
2. After `verify(...)` on that account, `roles` is unchanged (`["USER"]`).
3. Structural: `RegisterRequest` exposes no role component (guards against a future field that
   could bind a role).

Maps to spec FR-006, FR-007, FR-008 and SC-001.

## Note on granting ADMIN

ADMIN is granted only out-of-band (administrative DB update or a local-only dev toggle), never
through this endpoint or any other self-service flow. That path is intentionally not part of this
contract.
