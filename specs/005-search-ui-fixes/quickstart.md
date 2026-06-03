# Quickstart: Search Page UI Fixes

**Feature**: `005-search-ui-fixes` | **Date**: 2026-06-04

How to build, test, and manually verify the three fixes. Frontend-only; the backend does not need
to change, but a running backend (or mocked auth) is needed to exercise the logged-in nav.

## Prerequisites
- Node + npm (Angular 17+ toolchain) under `frontend/`.
- For the logged-in nav check: a backend reachable at `environment.apiBaseUrl` with a verified
  user that has a token balance (e.g. the seeded 5 free tokens), OR an auth mock in tests.

## Build, lint, test (Constitution V verification)
Run from `frontend/`:

```bash
npm ci            # if dependencies not installed
npm run build     # ng build - must compile clean
npm test          # ng test (Karma/Jasmine) - auth/guard/search specs green
npm run lint      # if a lint script is configured
```

If the Node/Angular runtime is unavailable in the execution environment, state that verification
is **static-only** and do not claim the app "works". Note specifically: these are reactivity /
visual / i18n fixes, so a static-only pass verifies **none** of US1-US3 behaviorally — report them
as *behaviorally unverified*, not merely "static-only", to avoid implying more assurance than a
parse provides.

> No Windows-executed scripts (`.ps1`/`.cmd`/`.bat`) are added or changed by this feature, so the
> non-ASCII script scan has no target; the Cyrillic `грн` lives only in `*.json`/`*.ts` (UTF-8),
> which the Angular toolchain consumes correctly.

## Manual verification

### 1. Authenticated navigation (FR-001/002/003)
1. Log in as a verified user, then navigate to `/search`.
2. **Expect**: the top nav shows Search, History, Providers, the token-balance pill, Account, and
   Sign out — and shows **no** "Sign in"/"Register".
3. Reload the page (hard refresh) while logged in. After the silent session restore resolves,
   **expect** the account menu (not guest CTAs) alongside the correct balance.
3a. **No-flash (US1-5)**: throttle the network (DevTools → Slow 3G) and hard-reload while logged
   in. **Expect** the nav shows the **neutral** state (brand + language toggle, no Sign in/Register)
   while the session resolves, then flips to the account menu — it must **never** show guest CTAs
   as your logged-in state, even briefly. (Deterministically covered by the in-flight-refresh
   component test.)
4. Click Sign out. **Expect** the nav reverts to "Sign in"/"Register" immediately.
5. As a guest (logged out), open a public page. **Expect** "Sign in"/"Register" are present.

### 2. Localized currency label (FR-004/005/006/007/011)
1. On `/search`, set the UI language to Ukrainian (UA in the header toggle).
2. **Expect** the Currency selector shows `грн` for the hryvnia option (and `USD` for dollars).
3. Toggle to English. **Expect** the option now reads `UAH`, updating **without** a page reload.
4. Toggle back to Ukrainian. **Expect** `грн` again, live.
5. Run a search in Ukrainian. **Expect** the results heading also shows `грн`, and the submitted
   amount/currency behavior is unchanged (the value sent is still the code `UAH`).

### 3. Full-width form (FR-008/009/010)
1. On a desktop-width window, view the search form.
2. **Expect** the field rows fill the card width — no large empty area to the right of the inputs.
3. Resize to a narrow/mobile width. **Expect** fields stack legibly with no horizontal scrollbar.
4. Confirm all fields, the goals counter, validation (e.g. empty amount shows the error), and the
   Search button still work.

## Done criteria
- `ng build` + `ng test` pass (or static-only declared).
- The three manual checks above pass on both desktop and mobile widths and in both languages.
- No change to any monetary value, search payload, or backend contract.
- Two role sub-agents (front-end lead + QA, incl. a build/parse step) have reviewed (Constitution
  VI).
