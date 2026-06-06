# Phase 0 Research: Restrict Providers to ADMIN role

No open `NEEDS CLARIFICATION` items remained from the spec. This document records the codebase
findings that fixed the technical approach and the design decisions taken.

## Codebase findings (current state)

| # | Area | Finding | Source |
|---|------|---------|--------|
| F1 | Role storage | `User.roles` is `List<String>`, defaulting to `["USER"]`; `User.newUnverified(...)` hard-codes `["USER"]`. | `backend/.../user/User.java:49,70` |
| F2 | Registration input | `RegisterRequest` has only `email` + `password` — **no role field**, so a client cannot supply a role at sign-up. | `backend/.../auth/dto/RegisterRequest.java` |
| F3 | Registration flow | `AuthService.register` calls `User.newUnverified` only; `verify`/`login`/`refresh` never mutate roles. | `backend/.../auth/AuthService.java` |
| F4 | Token roles | `JwtService.generateAccessToken` puts `roles` into the access-token `roles` claim. | `backend/.../common/security/JwtService.java:48` |
| F5 | Authority mapping | `JwtAuthenticationFilter` maps each role to `new SimpleGrantedAuthority("ROLE_" + r)`. | `backend/.../common/security/JwtAuthenticationFilter.java:56` |
| F6 | Current endpoint gate | `SecurityConfig` ends with `.anyRequest().authenticated()`; `GET /api/v1/providers` is therefore reachable by **any** authenticated user. | `backend/.../common/security/SecurityConfig.java:71` |
| F7 | Forbidden handling | An `accessDeniedHandler` is already wired, so a `403` is produced for authenticated-but-unauthorized requests. | `backend/.../common/security/SecurityConfig.java:74` |
| F8 | Role exposure to client | `UserProfileResponse.roles` -> `UserProfile.roles: string[]` is already returned by login/refresh/`/me`. | `backend/.../user/dto/UserProfileResponse.java`, `frontend/.../auth/auth.models.ts` |
| F9 | Frontend nav | `app.component.ts` renders the `/providers` link unconditionally inside the `authStatus()==='authenticated'` block. | `frontend/.../app.component.ts:61` |
| F10 | Frontend route | `/providers` uses `authGuard` only. | `frontend/.../app.routes.ts:82` |
| F11 | Test tooling | `spring-security-test` is already a backend test dependency; existing guard specs exist for the frontend. | `backend/pom.xml:85`, `frontend/.../auth.guards.spec.ts` |

**Conclusion**: User Story 3 (registration never grants ADMIN) is already satisfied by F1–F3.
The feature's net new work is the access controls of User Stories 1–2 plus regression tests that
pin the registration guarantee so it cannot silently regress.

## Decisions

### D1 — Backend: URL-based rule vs method security

- **Decision**: Add `requestMatchers(HttpMethod.GET, "/api/v1/providers").hasRole("ADMIN")` to the
  existing `authorizeHttpRequests` chain in `SecurityConfig`, placed before `anyRequest().authenticated()`.
- **Rationale**: The project already declares all access rules in `SecurityConfig` (public GET/POST
  arrays). Keeping one authorization model in one file is simpler and more auditable than
  introducing `@EnableMethodSecurity` + `@PreAuthorize` for a single endpoint. `hasRole("ADMIN")`
  matches the `ROLE_ADMIN` authority produced by F5 (Spring prepends the `ROLE_` prefix).
- **Alternatives considered**: `@PreAuthorize("hasRole('ADMIN')")` on `ProviderController` — rejected
  to avoid adding a second, scattered authorization mechanism for one route. Annotating the
  controller method with no method-security enabled would silently do nothing (a real footgun).

### D2 — Response codes

- **Decision**: Non-admin authenticated -> `403 Forbidden` (via the existing `accessDeniedHandler`,
  F7); unauthenticated -> `401 Unauthorized` (unchanged).
- **Rationale**: Matches the spec assumption (forbidden is acceptable; hiding existence is not a
  requirement) and the established error envelope. No new handler needed.

### D3 — Frontend: nav visibility

- **Decision**: Add an `isAdmin` computed signal to `AuthService`
  (`computed(() => (this._user()?.roles ?? []).includes('ADMIN'))`) and wrap the `/providers`
  nav `<a>` in `@if (auth.isAdmin())`.
- **Rationale**: Reuses the existing reactive `_user` signal that already carries `roles` (F8);
  the nav updates automatically on login/refresh/logout, consistent with how `authStatus()` and
  `tokenBalance()` already drive the shell.

### D4 — Frontend: route protection (deep-link)

- **Decision**: Add `adminGuard: CanActivateFn` that ensures a session (reusing the
  `authGuard` refresh path), then checks `auth.isAdmin()`; on failure it redirects to a safe
  authenticated location (`/account`). Apply `canActivate: [authGuard, adminGuard]` to `/providers`.
- **Rationale**: Mirrors the existing `verifiedGuard` shape (ensure session, then re-check a
  condition, else redirect). `/account` is a safe, always-available authenticated landing spot, so
  a non-admin deep-link never lands on a broken/empty Providers view.
- **Open consideration**: Roles come from the session/profile; on a hard reload the guard relies on
  the refresh-restored profile. Because the server check (D1) is authoritative, a momentarily stale
  client role cannot leak data — the API still returns `403`. This matches the documented
  best-effort-UX stance of the existing guards.

### D5 — Registration guarantee: lock-in test

- **Decision**: Add a backend regression test asserting that an account produced by the
  registration path holds exactly `["USER"]` (and not `ADMIN`), and a structural assertion that
  `RegisterRequest` exposes no role setter/field.
- **Rationale**: F1–F3 already hold; the test prevents a future change (e.g. accepting a roles
  field, or a seeder default flip) from silently granting ADMIN via self-service.

### D6 — Granting ADMIN (out of scope) for testability

- **Decision**: Do not add a self-service ADMIN path. For local manual verification, an ADMIN
  account is created out-of-band (e.g. a one-off DB role update, or an optional local-only dev
  seeder toggle). This is noted in quickstart but not built as a feature.
- **Rationale**: The spec explicitly scopes ADMIN-granting as out-of-band/administrative. Adding a
  self-service elevation would contradict User Story 3.

## Risks / mitigations

- **R1 — Hiding menu but not gating API** (the classic mistake): mitigated by D1 making the server
  the authoritative gate, independent of D3/D4 (FR-009). Backend test in D5/contracts proves it.
- **R2 — `hasRole` vs `hasAuthority` mismatch**: `hasRole("ADMIN")` expects authority `ROLE_ADMIN`,
  which is exactly what F5 produces. Pinned by the `403`/`200` backend test.
- **R3 — Stale client role after elevation**: acceptable per spec edge case; server re-checks every
  request. Role refresh occurs on next login/refresh.
