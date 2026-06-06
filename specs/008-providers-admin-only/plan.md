# Implementation Plan: Restrict Providers to ADMIN role

**Branch**: `008-providers-admin-only` | **Date**: 2026-06-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/008-providers-admin-only/spec.md`

## Summary

Gate the provider catalog behind the ADMIN role on three fronts: (1) the backend endpoint
`GET /api/v1/providers` must require `ROLE_ADMIN` (today it only requires authentication);
(2) the frontend must hide the "Providers" nav item and block the `/providers` route for
non-admins; (3) the public registration flow must remain incapable of producing an ADMIN
account. Findings 1–3 below show the registration side is already enforced in code
(`User.newUnverified` hard-codes `["USER"]`, `RegisterRequest` has no role field); this feature
adds the access controls plus regression tests that lock the registration guarantee in place.

The role plumbing already exists end to end: `User.roles` -> JWT `roles` claim ->
`JwtAuthenticationFilter` maps each to a `ROLE_<name>` authority -> `UserProfileResponse.roles`
-> frontend `UserProfile.roles`. So no schema or token change is needed; we only add an
authorization rule (server) and a role-aware guard + nav condition (client).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x / Angular 17+ standalone (frontend)
**Primary Dependencies**: Spring Boot 3.x, Spring Security (existing JWT filter chain), MongoDB
driver; Angular Router, ngx-translate, Angular signals. No new dependencies.
**Storage**: MongoDB 7 `users` collection (existing `roles: string[]` field; no migration).
**Testing**: JUnit 5 + Mockito + `spring-security-test` (already a dependency) for backend;
Jasmine/Karma for frontend (existing `*.spec.ts` pattern).
**Target Platform**: Single Spring Boot JAR behind TLS proxy; Angular SPA. Windows local dev via
Docker Compose.
**Project Type**: Web application (separate `backend/` and `frontend/`).
**Performance Goals**: No change; authorization is an in-filter check with no added I/O.
**Constraints**: Server-side check is authoritative; client gating is best-effort UX (mirrors the
existing `authGuard`/`verifiedGuard` doctrine). Non-admin authenticated callers receive `403`;
unauthenticated callers continue to receive `401`.
**Scale/Scope**: ~6 files touched (2 backend main, 1 backend test, 2 frontend main, 1 frontend
test). Granting ADMIN is an out-of-band/admin action and is out of scope.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0).

- [x] **I. MVP Discipline**: No new service/instance, queue, or scaling infra; single backend +
      single MongoDB + managed LLM preserved. Scope is a narrow authorization tightening — no new
      moving parts.
- [x] **II. Fixed Stack**: Angular 17+/Java 21/Spring Boot 3.x/MongoDB unchanged; no deprecated or
      new dependency added (`spring-security-test` already present). Provider access stays behind
      the existing repository/controller.
- [x] **III. LLM Guardrails**: Not applicable — no LLM code path is touched.
- [x] **IV. Financial Integrity**: Not applicable — no money or token-ledger code is touched.
- [x] **V. Encoding & Verification**: No Windows-executed scripts (`.ps1`/`.cmd`/`.bat`) are
      created or modified; only Java + TypeScript sources. Verification will be `mvn -q test`
      (backend) and the frontend unit specs; if a runtime is unavailable it will be declared
      static-only.
- [x] **VI. Multi-Role Review**: At least two role sub-agents (e.g. Security/BE lead + FE lead,
      plus QA) will review, including an actual compile/parse, before the work is marked done.

*Post-design re-check (after Phase 1): no new violations introduced; gates still pass.*

## Project Structure

### Documentation (this feature)

```text
specs/008-providers-admin-only/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── providers-endpoint.md
│   └── registration-role.md
├── checklists/
│   └── requirements.md  # Created by /speckit.specify
└── tasks.md             # Phase 2 output (/speckit.tasks - NOT created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/investguide/
├── common/security/
│   └── SecurityConfig.java          # CHANGE: add ADMIN rule for GET /api/v1/providers
├── catalog/
│   └── ProviderController.java      # (no change needed; URL rule covers it)
├── auth/
│   ├── AuthService.java             # (no change; registration already USER-only)
│   └── dto/RegisterRequest.java     # (no change; no role field by design)
└── user/User.java                   # (no change; newUnverified hard-codes ["USER"])

backend/src/test/java/com/investguide/
├── common/security/
│   └── ProviderAuthorizationTest.java   # NEW: 200 for ADMIN, 403 for USER, 401 anon
└── auth/
    └── RegistrationRoleTest.java        # NEW (or extend existing): registered account is USER-only

frontend/src/app/
├── core/auth/
│   └── auth.service.ts              # CHANGE: add `isAdmin` computed from user.roles
├── core/auth/auth.guards.ts         # CHANGE: add `adminGuard`
├── app.component.ts                 # CHANGE: gate the /providers nav link on isAdmin
├── app.routes.ts                    # CHANGE: /providers uses [authGuard, adminGuard]
└── core/auth/auth.guards.spec.ts    # CHANGE: cover adminGuard allow/deny
```

**Structure Decision**: Existing web-app layout (`backend/` Spring Boot + `frontend/` Angular).
Backend authorization is added in `SecurityConfig` (URL-based rule, consistent with how
`PUBLIC_GET`/`PUBLIC_POST` are already declared) rather than introducing `@EnableMethodSecurity`
and `@PreAuthorize`, to keep one authorization model in one place. Frontend mirrors the existing
guard pattern (`authGuard`/`verifiedGuard`) with a new `adminGuard` and a signal-driven nav
condition.

## Complexity Tracking

> No constitution violations. Table intentionally empty.
