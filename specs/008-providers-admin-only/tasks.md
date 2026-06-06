---
description: "Task list for Restrict Providers to ADMIN role"
---

# Tasks: Restrict Providers to ADMIN role

**Input**: Design documents from `/specs/008-providers-admin-only/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Test tasks ARE included. The plan's contracts call for authorization/regression tests,
and Constitution Principle VI requires verification by actual compile/parse + tests (not reading).

**Organization**: Tasks are grouped by user story. Note the inherent coupling for this feature:
the single backend rule that admits ADMIN (US1) is the same rule that denies USER (US2), and the
client `isAdmin` primitive is shared by both. Foundational Phase 2 holds that shared primitive;
US1 holds the enabling changes; US2 holds the deny-path verification; US3 is fully independent.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1, US2, US3 (maps to spec.md user stories)
- All paths are absolute from the repo root `C:\Users\xamcr\InvestGuideUA\`.

## Path Conventions

- Web app: backend at `backend/src/...`, frontend at `frontend/src/...`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the working context; no new dependencies or scaffolding are required.

- [X] T001 Confirm branch `008-providers-admin-only` is checked out and that backend test dep `spring-security-test` is present in `backend/pom.xml` (already verified in research F11) and the frontend unit-test runner is available.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared client role primitive used by both the nav visibility (US1/US2) and the route guard (US1/US2). Backend role plumbing already exists end-to-end (research F1–F8); no foundational backend work is needed.

**⚠️ CRITICAL**: T002 blocks the frontend tasks of US1 and US2.

- [X] T002 Add an `isAdmin` computed signal to `AuthService` in `frontend/src/app/core/auth/auth.service.ts`: `readonly isAdmin = computed(() => (this._user()?.roles ?? []).includes('ADMIN'));` (treats missing/empty roles as non-admin per INV-4).

**Checkpoint**: `auth.isAdmin()` is available to the shell and guards.

---

## Phase 3: User Story 1 - Administrator sees and uses the Providers area (Priority: P1) 🎯 MVP

**Goal**: An ADMIN sees the "Providers" nav item, the `/providers` route admits them, and `GET /api/v1/providers` returns the catalog.

**Independent Test**: Signed in as an ADMIN, the nav item is visible, the page loads, and the API returns `200` with the catalog.

### Implementation for User Story 1

- [X] T003 [US1] In `backend/src/main/java/com/investguide/common/security/SecurityConfig.java`, add a rule `.requestMatchers(HttpMethod.GET, "/api/v1/providers").hasRole("ADMIN")` placed BEFORE `.anyRequest().authenticated()` in the `authorizeHttpRequests` chain. (Matches the `ROLE_ADMIN` authority from `JwtAuthenticationFilter`; this rule simultaneously admits ADMIN for US1 and denies USER for US2.)
- [X] T004 [P] [US1] In `frontend/src/app/app.component.ts`, wrap the `/providers` nav anchor (currently line ~61, inside the `authStatus()==='authenticated'` block) in `@if (auth.isAdmin()) { ... }` so it renders only for admins. (Depends on T002.)
- [X] T005 [P] [US1] In `frontend/src/app/core/auth/auth.guards.ts`, add `adminGuard: CanActivateFn` that ensures a session (reuse the `authGuard` refresh path), then allows when `auth.isAdmin()` is true and otherwise returns `router.parseUrl('/account')`. (Depends on T002.)
- [X] T006 [US1] In `frontend/src/app/app.routes.ts`, change the `providers` route to `canActivate: [authGuard, adminGuard]` and import `adminGuard`. (Depends on T005.)

### Tests for User Story 1

- [X] T007 [P] [US1] Add `ProviderAuthorizationTest` in `backend/src/test/java/com/investguide/common/security/ProviderAuthorizationTest.java`: MockMvc against the real `SecurityFilterChain`, `@WithMockUser(roles = "ADMIN")` -> `GET /api/v1/providers` returns `200` (mock `ProviderRepository.findByActiveTrue()`). (Depends on T003. Verifies FR-001, SC-003.)
- [X] T008 [P] [US1] In `frontend/src/app/core/auth/auth.guards.spec.ts`, add a spec: an admin user (roles include `ADMIN`) is allowed through `adminGuard` (returns `true`). Add/adjust an `app.component` nav spec (or guard-adjacent spec) asserting the Providers link is rendered when `auth.isAdmin()` is true. (Depends on T004, T005. Verifies FR-004 admin side, SC-004 admin side.)

**Checkpoint**: An ADMIN can see and use Providers end to end; the backend gate is in place.

---

## Phase 4: User Story 2 - Regular user cannot see or reach Providers (Priority: P1)

**Goal**: A USER-only account never sees the nav item, cannot deep-link to `/providers`, and is refused by the API; unauthenticated callers are still refused.

**Independent Test**: Signed in as USER-only, the nav item is absent, `/providers` redirects to `/account`, and `GET /api/v1/providers` returns `403`; with no token it returns `401`.

> The enabling code already lands in US1 (T003 backend rule, T004 nav gate, T005/T006 guard). US2 verifies the deny path and the redirect target.

### Tests for User Story 2

- [X] T009 [P] [US2] Extend `ProviderAuthorizationTest` in `backend/src/test/java/com/investguide/common/security/ProviderAuthorizationTest.java` with: `@WithMockUser(roles = "USER")` -> `GET /api/v1/providers` returns `403`; unauthenticated request -> `401`. (Depends on T003. Verifies FR-002, FR-003, FR-009, SC-002, SC-005 API path.)
- [X] T010 [P] [US2] In `frontend/src/app/core/auth/auth.guards.spec.ts`, add specs: a USER-only user is redirected by `adminGuard` to `/account` (returns a `UrlTree` for `/account`); and assert the Providers nav link is NOT rendered when `auth.isAdmin()` is false. (Depends on T004, T005. Verifies FR-004 deny side, FR-005, SC-004 deny side, SC-005 UI path.)

**Checkpoint**: Regular and unauthenticated users are blocked at the API, the nav, and the route.

---

## Phase 5: User Story 3 - Registration never grants ADMIN (Priority: P1)

**Goal**: Accounts from the public registration flow are always `["USER"]` and never `ADMIN`, through registration and verification; no client-supplied role is honored.

**Independent Test**: Register a new account and inspect roles == `["USER"]` (no `ADMIN`); confirm roles unchanged after verification.

> Already enforced in code (research F1–F3: `User.newUnverified` hard-codes `["USER"]`; `RegisterRequest` has no role field). These tasks lock it in against regression. Fully independent of US1/US2 (different files).

### Tests for User Story 3

- [X] T011 [P] [US3] Add `RegistrationRoleTest` in `backend/src/test/java/com/investguide/auth/RegistrationRoleTest.java`: drive `AuthService.register(...)` (with mocked/in-memory `UserRepository` per the existing `acceptance` test-double pattern) and assert the persisted user's `roles` equals `List.of("USER")` and does not contain `"ADMIN"`. (Verifies FR-006, SC-001.)
- [X] T012 [P] [US3] In the same `RegistrationRoleTest`, assert that after `verify(...)` the account's roles remain `["USER"]` (verification does not grant ADMIN). (Verifies FR-008.)
- [X] T013 [P] [US3] In the same `RegistrationRoleTest`, add a structural assertion that `RegisterRequest` (`backend/src/main/java/com/investguide/auth/dto/RegisterRequest.java`) exposes no role component (e.g. reflectively assert its record components are exactly `email`, `password`), guarding against a future role-binding field. (Verifies FR-007.)

**Checkpoint**: The registration USER-only guarantee is pinned by tests.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verification and mandatory review.

- [X] T014 Run backend verification `mvn -q test` from `backend/` (focus: `ProviderAuthorizationTest`, `RegistrationRoleTest`, plus the existing `ProviderControllerTest`). If no JDK/Maven runtime is available, state explicitly that verification is static-only. (Constitution Principle V.)
- [X] T015 [P] Run the frontend unit specs covering `auth.guards.spec.ts` and the nav assertion; confirm green.
- [X] T016 [P] Confirm no Windows-executed scripts (`.ps1`/`.cmd`/`.bat`) were modified by this feature (only `.java`/`.ts` changed), so the non-ASCII scan gate is N/A; record this in the completion note. (Constitution Principle V.)
- [X] T017 Mandatory multi-role sub-agent review (Constitution Principle VI): at least two role sub-agents (e.g. Security/Backend lead + Frontend/QA lead) review the diff, INCLUDING an actual compile/parse step; apply or report findings before closing.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: After Setup. T002 blocks frontend tasks in US1/US2.
- **US1 (Phase 3)**: After Foundational. Contains the enabling changes (backend + frontend).
- **US2 (Phase 4)**: Verification depends on US1's shared-file changes (T003/T004/T005/T006).
- **US3 (Phase 5)**: Independent of US1/US2 — can run anytime after Setup.
- **Polish (Phase 6)**: After all desired stories.

### Key task dependencies

- T002 -> T004, T005
- T005 -> T006
- T003 -> T007, T009
- T004, T005 -> T008, T010
- T003, T004, T005, T006 -> (US2 tests T009, T010)
- US3 (T011–T013) has no dependency on US1/US2.
- T014–T017 after the implementation/tests they verify.

### Parallel Opportunities

- **US3 in parallel with US1/US2**: T011, T012, T013 touch only auth-module test files — can proceed independently of all other work (after Setup).
- Within US1: T004 and T005 are different files ([P]); T007 (backend) and T008 (frontend) are different stacks ([P]).
- Within US2: T009 (backend) and T010 (frontend) are different stacks ([P]).
- Polish: T015 and T016 are [P].

---

## Parallel Example: kick off independent tracks after Setup

```bash
# Track A (shared primitive first):
Task: "T002 Add isAdmin computed to auth.service.ts"

# Track B (fully independent registration lock-in, no shared files):
Task: "T011 RegistrationRoleTest: registered account roles == [USER]"
Task: "T012 RegistrationRoleTest: roles unchanged after verify"
Task: "T013 RegistrationRoleTest: RegisterRequest exposes no role component"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup -> Phase 2 Foundational (T002) -> Phase 3 US1 (T003–T008).
2. **STOP and VALIDATE**: ADMIN sees + uses Providers; backend returns 200 for ADMIN.
3. The same changes already block non-admins — proceed to US2 verification.

### Incremental Delivery

1. Foundational + US1 -> ADMIN access works AND non-admins are blocked (MVP, security goal met).
2. US2 -> tests prove the deny path (403/401, hidden nav, redirect).
3. US3 -> tests pin the registration USER-only guarantee (can be done in parallel from the start).
4. Polish -> run tests + mandatory review.

---

## Notes

- [P] = different files, no incomplete-task dependency.
- The backend gate (T003) is the authoritative control (FR-009); client gating (T004–T006) is best-effort UX, consistent with the existing `authGuard`/`verifiedGuard` doctrine.
- Granting ADMIN is out-of-band (see quickstart) and intentionally NOT a task.
- No i18n changes: `nav.providers` / `title.providers` keys already exist.
- No DB migration: `User.roles` already exists.
- Commit after each task or logical group; do not skip the Phase 6 verification gates.

## Implementation completion note (2026-06-05)

All tasks complete and verified.

- **Verification**: backend `mvn test` for the new/affected classes -> `ProviderAuthorizationTest`
  5/5, `RegistrationRoleTest` 3/3, BUILD SUCCESS. Frontend `npm run test:ci` -> 67/67 SUCCESS.
- **Scope adjustment from review (T017)**: the multi-role review surfaced that the site **footer**
  (`frontend/src/app/core/layout/footer.component.ts`) also linked to `/providers`, ungated and
  visible to guests/USERs. Gated it with the same `@if (auth.isAdmin())` so the Providers link is
  admin-only everywhere (satisfies FR-004 fully). `/providers` was already a PRIVATE_PREFIX
  (noindex, not in sitemap), so hiding it for unauthenticated prerender is correct, not a regression.
- **Review NIT closed**: added two real-Bearer-token cases to `ProviderAuthorizationTest`
  (`realAdminTokenGetsCatalog`, `realUserTokenIsForbidden`) so the JWT roles-claim ->
  `ROLE_ADMIN` authority -> `hasRole("ADMIN")` chain is exercised end-to-end, not only via
  `@WithMockUser`.
- **Encoding gate (Constitution V)**: only `.java` / `.ts` files changed; no `.ps1`/`.cmd`/`.bat`
  touched, so the non-ASCII Windows-script scan is N/A.
