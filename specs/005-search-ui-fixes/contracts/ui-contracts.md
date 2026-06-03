# Phase 1 Contracts: Search Page UI Fixes

**Feature**: `005-search-ui-fixes` | **Date**: 2026-06-04

This is a frontend SPA feature. It exposes **no new or changed external/API contracts**. The
relevant contracts are the **observable UI behaviors** (the app's contract with its users) plus
the unchanged backend contracts it continues to honor.

## API / backend contracts — UNCHANGED

No backend endpoint, request, or response is added or modified.

| Contract | Status |
|---|---|
| `POST /auth/login`, `POST /auth/refresh`, `GET /me` | Unchanged — consumed exactly as before; access token stays in-memory, refresh stays HttpOnly cookie. |
| `POST /investments/search` request body (`amount`, `currency` enum `UAH\|USD`, ...) | Unchanged — `currency` is still submitted as the **code**, not the localized label. |
| Money representation (integer minor units / kopiykas) | Unchanged — currency localization is label-only. |

## UI behavior contracts (this feature)

### UC-1 — Navigation reflects authentication state (maps FR-001, FR-002, FR-003)
- **Given** auth status is `authenticated` (active session token), **then** the top nav renders the
  authenticated menu (Search, History, Providers, balance, Account, Sign out) and renders neither
  "Sign in" nor "Register".
- **Given** auth status is `guest`, **then** the top nav renders "Sign in" and "Register".
- **Given** auth status is `unknown` (startup silent `refresh()` still in flight), **then** the top
  nav renders a neutral state and renders **neither** the account menu **nor** the guest CTAs — so a
  logged-in user never sees guest CTAs presented as their state (US1-5 no-flash).
- **Contract guarantee**: the rendered branch updates **reactively** whenever the session is
  established (`applySession`), cleared (`clearSession`/`logout`), or resolved from `unknown`, with
  no manual refresh.

### UC-2 — Localized currency label (maps FR-004, FR-005, FR-006, FR-007, FR-011)
- **Given** UI language `uk`, **then** the currency selector renders the `UAH` option as `грн`.
- **Given** UI language `en`, **then** it renders the `UAH` option as `UAH`.
- **Given** the user toggles language at runtime, **then** the label updates without a page reload.
- **In all cases**, the `USD` option renders as `USD`, the selected form value remains the code
  (`UAH`/`USD`), and the submitted search payload + any monetary computation are byte-for-byte
  unchanged.

### UC-3 — Full-width search form (maps FR-008, FR-009, FR-010)
- **Given** a desktop-width viewport, **then** the form's field rows fill the width of the
  containing card (no large empty right-hand area).
- **Given** a narrow/mobile viewport, **then** fields stack/reflow without horizontal overflow.
- **In all cases**, every field, label, validation message, and the submit action remain present
  and functional (no behavioral change).

## Accessibility contract (preserved)
- Nav: existing `aria-*` on the language toggle, mobile menu disclosure, and the `aria-live`
  announcement region are retained.
- Form: existing `label`/`for`, `aria-invalid`, and `aria-describedby` wiring on the amount field
  and the role="alert" error are retained. Localizing option text does not change `<select>`
  semantics or the control value.

## Verification hooks
- Unit (Karma/Jasmine), `auth.service.spec.ts`: assert reactivity via **read-before-set** ordering
  — read `isAuthenticated()` (or auth status) *before* `applySession`, then assert it changes after
  — because the existing post-login assertions pass even with the non-reactive bug. A component
  `TestBed` test rendering the guest branch → applying a session → `detectChanges()` → asserting the
  DOM switched is the strongest guard.
- Component (new `search.component.spec.ts`): currency option text resolves through the `igCurrency`
  pipe (not a hardcoded literal) and updates on language switch; `.ig-form--wide` is not width-pinned.
- No-flash (US1-5): a component test that holds `refresh()` **pending** and asserts the guest CTAs
  are NOT rendered while auth status is `unknown`, then resolves authenticated and asserts the
  account menu appears.
- Manual (quickstart): nav menu, neutral-state-then-flip on throttled reload, live language switch,
  and form width across desktop/mobile viewports.
