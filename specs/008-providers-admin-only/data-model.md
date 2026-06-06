# Phase 1 Data Model: Restrict Providers to ADMIN role

This feature introduces **no new persisted entities and no schema migration**. It relies on the
existing role field. The model below documents the relevant existing shapes and the invariants this
feature depends on / strengthens.

## Entity: User (existing, `users` collection)

| Field | Type | Notes for this feature |
|-------|------|------------------------|
| `id` | String | unchanged |
| `email` | String (unique, lowercased) | unchanged |
| `passwordHash` | String (BCrypt) | unchanged |
| `emailVerified` | boolean | unchanged |
| `tokenBalance` | int (minor units rules unaffected) | unchanged |
| `roles` | `List<String>` | **central to this feature.** Default & registration value = `["USER"]`. May additionally contain `"ADMIN"` only via an out-of-band/admin action. |
| `createdAt` / `updatedAt` | Instant | unchanged |

### Role values

- `USER` — assigned to every self-registered account. Default for `User.roles` and the explicit
  value set by `User.newUnverified(...)`.
- `ADMIN` — required to access the provider catalog. Never assigned by any self-service path
  (registration, verification, login, refresh). Present only via administrative/out-of-band action.

### Invariants (this feature)

- **INV-1**: An account created through registration has `roles == ["USER"]` exactly. (Already
  enforced; pinned by a regression test.)
- **INV-2**: No self-service operation adds `ADMIN` to `roles`.
- **INV-3**: An account is treated as administrator iff `roles` contains `"ADMIN"` (regardless of
  other roles also present).
- **INV-4**: Missing/empty `roles` is treated as non-admin (deny).

## Derived/transient shapes (no persistence change)

### Access token `roles` claim (existing)

`JwtService.generateAccessToken(userId, roles)` copies `User.roles` into the JWT `roles` claim;
`JwtAuthenticationFilter` maps each entry `r` to authority `ROLE_<r>`. The backend authorization
rule checks for `ROLE_ADMIN`.

### Client profile (existing)

`UserProfile.roles: string[]` (frontend) mirrors `UserProfileResponse.roles` (backend) and is the
source for the client `isAdmin` derivation. No field added.

## State transitions

None. Roles are not mutated by any flow in this feature. ADMIN assignment/removal happens outside
the application's self-service surface.
