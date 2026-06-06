# Quickstart: Restrict Providers to ADMIN role

How to implement and verify this feature. Order follows the spec's P1 stories; the backend gate
(authoritative) goes first, then the client UX, then the registration lock-in.

## 1. Backend — require ADMIN on the catalog endpoint (FR-001/002/003/009)

In `backend/src/main/java/com/investguide/common/security/SecurityConfig.java`, add a rule before
`anyRequest().authenticated()`:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/providers").hasRole("ADMIN")
```

`hasRole("ADMIN")` matches the `ROLE_ADMIN` authority that `JwtAuthenticationFilter` already
produces from the token `roles` claim. No controller change needed.

## 2. Backend — authorization test (SC-002/003)

Add `ProviderAuthorizationTest` (MockMvc against the real filter chain; `spring-security-test` is
available):

- `@WithMockUser(roles = "ADMIN")` -> `GET /api/v1/providers` => `200`.
- `@WithMockUser(roles = "USER")` -> `403`.
- no user -> `401`.

## 3. Frontend — `isAdmin` + nav visibility (FR-004)

In `frontend/src/app/core/auth/auth.service.ts`:

```ts
readonly isAdmin = computed(() => (this._user()?.roles ?? []).includes('ADMIN'));
```

In `app.component.ts`, wrap the providers link:

```html
@if (auth.isAdmin()) {
  <a routerLink="/providers" routerLinkActive="is-active" ariaCurrentWhenActive="page"
     (click)="menuOpen.set(false)">{{ 'nav.providers' | translate }}</a>
}
```

(It already sits inside the `authStatus()==='authenticated'` block.)

## 4. Frontend — `adminGuard` on the route (FR-005)

In `auth.guards.ts`, add `adminGuard` that ensures a session (reuse the refresh path) then checks
`auth.isAdmin()`, redirecting non-admins to `/account`. In `app.routes.ts`:

```ts
{ path: 'providers', title: 'title.providers',
  canActivate: [authGuard, adminGuard], loadComponent: ... }
```

Cover allow (admin) and deny/redirect (USER) in `auth.guards.spec.ts`.

## 5. Backend — registration role lock-in (FR-006/007/008, SC-001)

Add/extend a test proving a registered account is `["USER"]` and not `ADMIN`, and that role is
unchanged after verification. (Already true in code via `User.newUnverified` + role-less
`RegisterRequest`; the test prevents regression.)

## Manual verification (local Docker Compose)

Granting ADMIN is out-of-band. For local testing, elevate the dev user once:

```
// in the Mongo shell against the local DB
db.users.updateOne({ email: "dev@investguide.local" }, { $set: { roles: ["USER", "ADMIN"] } })
```

Then, in the app on `http://localhost:4200` (dev) / `:8081` (compose):

- Log in as a USER-only account -> no "Providers" nav item; visiting `/providers` redirects to
  `/account`; a direct `GET /api/v1/providers` with that token returns `403`.
- Log in as the elevated ADMIN -> "Providers" item visible; page loads; the API returns `200`.

(Re-login after the DB change so the new role lands in a fresh token/profile.)

## Verification gates (constitution V/VI)

- Backend: `mvn -q test` (or state static-only if no JDK/Maven runtime is available).
- Frontend: run the unit specs for the guard/nav.
- No Windows-executed scripts (`.ps1`/`.cmd`/`.bat`) are touched, so the non-ASCII scan is N/A for
  this change; confirm no such files were modified.
- Two role sub-agents review (Security/BE + FE/QA) including a compile/parse step.
