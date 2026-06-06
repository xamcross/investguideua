# Feature Specification: Restrict Providers to ADMIN role

**Feature Branch**: `008-providers-admin-only`  
**Created**: 2026-06-05  
**Status**: Draft  
**Input**: User description: "\"Providers\" menu item, as well as api endpoint should not be accessible for regular users with role USER. only those with role ADMIN can see the menu item and have access to the api endpoint. Users created through regular registration flow can never receive ADMIN role, only USER"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrator sees and uses the Providers area (Priority: P1)

An administrator signs in and needs to view the provider catalog. They see the "Providers" item in the main navigation, open it, and the catalog loads successfully.

**Why this priority**: Without this, the feature delivers no value — administrators must retain full access to the area they alone are entitled to. This is the positive path that proves the capability still works for the intended audience.

**Independent Test**: Sign in as a user with the ADMIN role, confirm the "Providers" navigation item is visible, open it, and confirm the provider catalog data is returned and displayed.

**Acceptance Scenarios**:

1. **Given** an authenticated user holding the ADMIN role, **When** they view the main navigation, **Then** the "Providers" item is visible.
2. **Given** an authenticated ADMIN user, **When** they open the Providers area, **Then** the active provider catalog loads and is displayed.
3. **Given** an authenticated ADMIN user, **When** the provider data is requested on their behalf, **Then** the request is authorized and returns the catalog.

---

### User Story 2 - Regular user cannot see or reach Providers (Priority: P1)

A regular user (role USER) signs in. They must not see the "Providers" navigation item, and any attempt to reach the provider catalog — by direct link or by calling the data source directly — must be refused.

**Why this priority**: This is the core protection the feature exists to deliver. A regular user must be blocked both visually (no menu item) and at the data boundary (request refused), so hiding the menu alone is not sufficient.

**Independent Test**: Sign in as a user with only the USER role, confirm the "Providers" navigation item is absent, attempt to open the Providers area by direct navigation, and confirm access is refused; independently confirm a direct request for the provider data on behalf of a USER-only account is refused.

**Acceptance Scenarios**:

1. **Given** an authenticated user holding only the USER role, **When** they view the main navigation, **Then** the "Providers" item is not shown.
2. **Given** an authenticated USER-only user, **When** they navigate directly to the Providers area, **Then** they are denied access and are not shown the provider catalog.
3. **Given** an authenticated USER-only user, **When** a request for the provider data is made on their behalf, **Then** the request is refused with an authorization error and no catalog data is returned.
4. **Given** an unauthenticated visitor, **When** a request for the provider data is made, **Then** it is refused (authentication required) as it is today.

---

### User Story 3 - Registration never grants ADMIN (Priority: P1)

A new person registers through the public sign-up flow. The resulting account is always created with the USER role only and can never obtain the ADMIN role through registration, verification, or any other self-service action.

**Why this priority**: The restriction in Stories 1 and 2 is only meaningful if a regular user cannot escalate to ADMIN. The registration path is the obvious escalation surface, so it must be provably incapable of granting ADMIN.

**Independent Test**: Complete the public registration flow with a new account, then inspect the account's roles and confirm it holds USER and not ADMIN; repeat after email verification to confirm the role set is unchanged.

**Acceptance Scenarios**:

1. **Given** a new visitor, **When** they complete public registration, **Then** the created account holds the USER role and does not hold the ADMIN role.
2. **Given** a freshly registered account, **When** it completes email verification, **Then** its role set remains USER only (verification does not grant ADMIN).
3. **Given** the public registration flow, **When** any registration input attempts to request or assert a role, **Then** that input is ignored and the account is still created as USER only.

---

### Edge Cases

- **Role added while signed in**: If an account is elevated to ADMIN (or demoted from it) while a session is active, the change takes effect on the next session refresh / re-authentication; a stale session reflecting the old role is acceptable short-term because the data boundary re-checks authorization on every request.
- **Account with both USER and ADMIN roles**: Treated as an administrator — the ADMIN role grants access regardless of other roles present.
- **Direct deep-link by a USER-only account**: Navigation must not display the catalog; the user is sent to a safe location (e.g., redirected away) rather than shown an empty or broken Providers view.
- **Manipulated client state**: A regular user who alters client-side state to reveal the hidden menu item still cannot retrieve provider data, because the data boundary independently enforces the ADMIN requirement.
- **Missing or empty role information**: An account whose role information is missing or empty is treated as not-ADMIN and is denied access.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST restrict access to the provider catalog data so that only authenticated users holding the ADMIN role can retrieve it.
- **FR-002**: The system MUST refuse provider catalog data requests made on behalf of authenticated users who do not hold the ADMIN role, returning an authorization-denied result and no catalog data.
- **FR-003**: The system MUST continue to refuse provider catalog data requests from unauthenticated callers (authentication remains required).
- **FR-004**: The system MUST display the "Providers" navigation item only to users holding the ADMIN role, and MUST hide it from all other users (including USER-only and unauthenticated states).
- **FR-005**: The system MUST prevent a USER-only account from viewing the Providers area via direct navigation, directing them to a safe location instead of the catalog view.
- **FR-006**: The system MUST ensure that accounts created through the public registration flow are assigned the USER role only and never the ADMIN role.
- **FR-007**: The system MUST ignore any client-supplied role information during registration so that a caller cannot self-assign ADMIN (or any other role) at sign-up.
- **FR-008**: The system MUST ensure that email verification, refresh, login, or any other self-service action on a registered account does not grant the ADMIN role.
- **FR-009**: The system MUST enforce the ADMIN requirement at the data boundary independently of the navigation visibility rule, so that hiding the menu item is not the only control.
- **FR-010**: The system MUST treat an account holding the ADMIN role as authorized for the Providers area regardless of any additional roles it also holds.

### Key Entities *(include if feature involves data)*

- **User account**: Represents a registered person. Has a set of roles. Public registration yields exactly the USER role. The ADMIN role can only be present through an out-of-band/administrative process, never through self-service.
- **Role**: A named authorization level associated with an account. Relevant values: USER (default for all self-registered accounts) and ADMIN (required for the Providers area).
- **Provider catalog**: The collection of active provider entries shown in the Providers area. Reading it now requires the ADMIN role.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of accounts created through the public registration flow hold the USER role and zero hold the ADMIN role.
- **SC-002**: 100% of provider catalog data requests made on behalf of non-ADMIN accounts are refused with no catalog data disclosed.
- **SC-003**: 100% of provider catalog data requests made on behalf of ADMIN accounts succeed and return the catalog.
- **SC-004**: The "Providers" navigation item is visible in 0% of USER-only and unauthenticated sessions and 100% of ADMIN sessions.
- **SC-005**: A regular user cannot reach the provider catalog through any tested path (hidden menu, direct deep-link, or direct data request) — all such paths are blocked in 100% of attempts.
- **SC-006**: No regression for administrators: an ADMIN user can locate and open the Providers area and see the catalog within the same number of steps as before the change.

## Assumptions

- The existing role model uses the role names USER and ADMIN, and these are the only roles relevant to this feature; no new roles are introduced.
- The application already exposes each authenticated user's role set to the client (used to drive navigation visibility), so no new mechanism is required to determine whether to show the "Providers" item.
- Granting the ADMIN role is an administrative/out-of-band action (e.g., a controlled seeding or back-office process) and is explicitly out of scope for this feature, which only guarantees that self-service paths never grant it.
- When a non-ADMIN user is refused at the data boundary, an authorization-denied (forbidden) result is the expected response; revealing whether the resource exists is not a concern beyond denying access.
- Client-side navigation gating is treated as best-effort UX only; the authoritative control is the server-side authorization check on the data request.
- The provider catalog remains read-only; this feature changes who may read it, not what operations are available.
