# Phase 1 Data Model: Search Page UI Fixes

**Feature**: `005-search-ui-fixes` | **Date**: 2026-06-04

This feature introduces **no persistent (MongoDB) entities** and **no API schema changes**. It is
a frontend presentation/reactivity fix. The only "model" is in-memory client UI state. Documented
here for completeness.

## Client UI state (no persistence)

### AuthState (existing, in `AuthService`)
The fix makes one field reactive; the shape and semantics are otherwise unchanged.

| Field | Type | Reactive? | Notes |
|---|---|---|---|
| access token | `string \| null` | **CHANGED: now a signal** | In-memory only; never persisted (security model unchanged). Previously a plain field, which froze `isAuthenticated`. |
| `_user` | `UserProfile \| null` (signal) | yes (unchanged) | Loaded profile incl. `tokenBalance`, `emailVerified`. |
| `isAuthenticated` | `boolean` (computed) | yes (now correctly) | Derived from the access-token signal: `true` iff a session token is present. |
| auth status | `'unknown' \| 'authenticated' \| 'guest'` (signal/computed) | **NEW** | `unknown` until the startup silent `refresh()` settles, then `authenticated`/`guest`. Drives the no-flash neutral nav (US1-5). |
| `tokenBalance` | `number` (computed) | yes (unchanged) | `_user()?.tokenBalance ?? 0`. |

**State transitions** (unchanged behavior, now reactively reflected in the nav):
- `applySession(res)` — sets access token + `_user` → `isAuthenticated` becomes `true` → nav shows
  account menu.
- `clearSession()` / `logout()` — clears both → `isAuthenticated` becomes `false` → nav shows guest
  CTAs.
- `loadMe()` — updates `_user` only; does **not** assert authentication on its own (access-token
  signal remains the source of truth for `isAuthenticated`).

**Invariant**: `isAuthenticated` MUST reflect the *current* presence of a session token at all
times (the defect was a stale, never-recomputed value).

### Navigation menu (derived view state, in `app.component.ts`)
Not a stored entity — a rendered projection of `AuthState`.

| Auth status | Menu items shown |
|---|---|
| `unknown` (startup refresh in flight) | **Neutral** — neither account menu nor guest CTAs (brand + language toggle only) |
| `authenticated` | Search, History, Providers, Token balance pill, Account, Sign out |
| `guest` | Sign in, Register |

The markup already encodes the authenticated/guest branching; this feature makes the branch input
reactive AND adds the neutral `unknown` branch so a logged-in user never momentarily sees guest
CTAs during silent session restore (US1-5).

### Currency label (presentation value object)
Display-only mapping; carries **no monetary semantics** (Constitution IV).

| Currency code (canonical, unchanged) | Label in `uk` | Label in `en` |
|---|---|---|
| `UAH` | `грн` | `UAH` |
| `USD` | `USD` | `USD` |

- The form control value and all amount/minor-unit math continue to use the **code** (`UAH`/`USD`).
- The label is resolved at render time via the existing `CurrencyLabelPipe` (`currency.<code>`
  translation key). Switching language re-renders the label (impure pipe). No amount is ever
  derived from the label.

## Validation rules (unchanged)
- Search form validation is unchanged: `amount` required and `> 0`; `goals` <= 280; currency is a
  required enum of `UAH | USD`. The layout change does not alter any validator or control.

## Out of scope (explicitly no model change)
- `users`, `searchRequests`, `providers`, `tokenPacks`, `payments` collections — untouched.
- Token-ledger accounting, free-token grant, payment crediting — untouched.
- Auth token storage model (in-memory access token + HttpOnly refresh cookie) — semantics
  unchanged; only the in-memory field's *reactivity* changes.
