---
description: "Task list for Search Page UI Fixes"
---

# Tasks: Search Page UI Fixes

**Input**: Design documents from `/specs/005-search-ui-fixes/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui-contracts.md, quickstart.md

**Tests**: INCLUDED and REQUIRED. The plan + Constitution VI mandate them, and the QA review proved
that a naive test passes while the bug ships — so the US1 regression test MUST be written first and
MUST fail against the current code before the fix.

**Organization**: Grouped by user story. The three fixes are independent except that US2 and US3
both edit `frontend/src/app/features/search/search.component.ts` (and its spec), so they must not
run in parallel with each other.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 (Setup, Foundational, Polish have no story label)
- Exact file paths are included in each task. All paths are repo-relative.

## Path Conventions

Web app, **frontend only** for this feature. All source under `frontend/src/app/`. Backend
(`com.investguide.*`) and MongoDB are NOT touched. Test runner: Karma + Jasmine (`ng test`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish a clean baseline so regressions are attributable to this work.

- [X] T001 Establish baseline in `frontend/`: run `npm ci` (if needed), then `npm run build` and `npm test`; confirm they pass BEFORE any change. If the Node/Angular runtime is unavailable, record that verification is static-only and that behavior is unverified (per quickstart.md).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Cross-story blocking prerequisites.

**None.** The three fixes are independent: US1 touches `auth.service.ts` + `app.component.ts`; US2
and US3 touch only `search.component.ts`. There is no shared foundation to build first, so user
stories may begin immediately after Setup. (Section kept intentionally for template traceability.)

**Checkpoint**: Baseline green → user story work can begin.

---

## Phase 3: User Story 1 - Authenticated user sees an account menu, not guest CTAs (Priority: P1) 🎯 MVP

**Goal**: A logged-in user always sees the account menu (Search, History, Providers, token balance,
Account, Sign out) and never "Sign in"/"Register" — including immediately after a silent session
restore, with no guest-CTA flash.

**Independent Test**: Sign in, open `/search` → account menu shown, no Sign in/Register. Throttle
network and hard-reload while logged in → nav shows a neutral state (no guest CTAs) then flips to
the account menu. Sign out → guest CTAs return. (spec.md US1 scenarios 1-5.)

### Tests for User Story 1 (write FIRST, must FAIL against current code)

- [X] T002 [P] [US1] In `frontend/src/app/core/auth/auth.service.spec.ts`, add a **read-before-set reactivity** test: read `service.isAuthenticated()` BEFORE applying a session (expect `false`), then `applySession`/`login`, then assert `isAuthenticated()` is now `true` on the SAME service instance (no re-injection); symmetrically read `true`, `clearSession()`, assert `false`. This MUST fail with the current non-reactive `computed()` (research.md "Testing" decision; the existing post-login asserts at ~lines 53/118 already pass even when broken — do not rely on them).
- [X] T003 [P] [US1] Create `frontend/src/app/app.component.spec.ts` with a TestBed **no-flash** test: stub `AuthService.refresh()` to return a still-pending observable; render the shell and assert that while auth status is `unknown` the nav shows NEITHER the account menu NOR "Sign in"/"Register" (neutral state). Then (a) resolve refresh as authenticated → assert account menu appears and guest CTAs absent; (b) in a second case resolve as failure → assert guest CTAs appear. Must fail until T005/T006 land. (contracts/ui-contracts.md UC-1; spec.md US1-5.)

### Implementation for User Story 1

- [X] T004 [US1] In `frontend/src/app/core/auth/auth.service.ts`: convert the in-memory access token from the plain field `#accessToken` to `readonly #accessToken = signal<string | null>(null)`; make `get accessToken()` return `this.#accessToken()`; update `applySession()` and `clearSession()` to use `this.#accessToken.set(...)`; change `isAuthenticated` to `computed(() => this.#accessToken() !== null)`. Keep the public API (`accessToken` getter, `isAuthenticated`) byte-identical for callers. (research.md R1; data-model.md AuthState.)
- [X] T005 [US1] In `frontend/src/app/core/auth/auth.service.ts`: add a tri-state auth status exposed to the shell — `authStatus` returning `'unknown' | 'authenticated' | 'guest'`. Back it with an `#authResolved = signal(false)` that flips `true` once the startup silent refresh settles (success OR failure); derive `authStatus = computed(() => !this.#authResolved() ? 'unknown' : (this.#accessToken() !== null ? 'authenticated' : 'guest'))`. Provide a method (e.g. `markResolved()`) or set it inside `applySession`/`clearSession` so the startup refresh transition is captured. Do NOT let a `loadMe()`-only profile (no token) read as authenticated. (research.md R1 no-flash decision; data-model.md.)
- [X] T006 [US1] In `frontend/src/app/app.component.ts`: (a) in `ngOnInit`, ensure the startup `refresh()` subscribe marks the auth status resolved on BOTH next and error (so `unknown` → `authenticated`/`guest`); (b) update the nav template to branch on `auth.authStatus()`: `unknown` → neutral (brand + language toggle only, no menu, no CTAs), `authenticated` → existing account menu, `guest` → existing Sign in/Register. Preserve all existing `aria-*`, the mobile menu toggle, balance pill, and sign-out. (contracts/ui-contracts.md UC-1.)
- [X] T007 [US1] Verify consumers of the changed API still compile and behave: `frontend/src/app/core/auth/auth.interceptor.ts` (synchronous `const token = auth.accessToken;` at ~line 39 — a plain signal read, safe), `frontend/src/app/core/auth/auth.guards.ts` (reads `isAuthenticated()`/`refresh()` only), and `frontend/src/app/features/landing/landing.component.ts` (`isAuthenticated()`). Re-run `auth.guards.spec.ts`; confirm no regression.

**Checkpoint**: US1 fully functional — `npm test` green (incl. T002/T003 now passing), logged-in nav correct with no flash. This is the MVP; can ship independently.

---

## Phase 4: User Story 2 - Currency label localized to Ukrainian (Priority: P2)

**Goal**: The currency selector (and any displayed currency) reads `грн` in Ukrainian and `UAH` in
English, updating live on language toggle, with no change to any amount/payload.

**Independent Test**: On `/search` switch language to Ukrainian → currency option reads `грн`; switch
to English → `UAH`, live without reload; USD stays `USD` in both. (spec.md US2 scenarios 1-5.)

### Tests for User Story 2 (write FIRST)

- [X] T008 [P] [US2] Create `frontend/src/app/features/search/search.component.spec.ts` (TestBed): assert the currency `<option>` visible text resolves through `igCurrency` — `грн` under `uk`, `UAH` under `en` — and updates when the language switches at runtime (impure pipe); assert the `USD` option text is `USD` in both languages; assert the form control `currency` value remains the CODE (`UAH`/`USD`) and the submitted payload is unchanged (FR-007). Must fail against the current hardcoded literals. (contracts/ui-contracts.md UC-2; research.md R2.)

### Implementation for User Story 2

- [X] T009 [US2] In `frontend/src/app/features/search/search.component.ts` template (~lines 60-63): change the hardcoded `<option value="UAH">UAH</option>` and `<option value="USD">USD</option>` to bind their text via the already-imported pipe: `{{ 'UAH' | igCurrency }}` and `{{ 'USD' | igCurrency }}`, keeping the `value` attributes as the codes. No translation-file edits (keys `currency.UAH`/`currency.USD` already exist in `frontend/public/i18n/uk.json` and `en.json`). (research.md R2; FR-004..FR-007, FR-011.)

**Checkpoint**: US1 + US2 both work independently; currency label localizes live, money behavior unchanged.

---

## Phase 5: User Story 3 - Search form fields use the full component width (Priority: P3)

**Goal**: The search form fields fill the card width on desktop instead of being crammed into a
narrow left column, while staying legible (no horizontal overflow) on mobile.

**Independent Test**: On a desktop-width window the form fields span the card width (no large empty
gap right of the inputs); on a narrow window fields stack with no horizontal scrollbar; all fields,
validation, and submit still work. (spec.md US3 scenarios 1-4.)

### Tests for User Story 3 (write FIRST)

- [X] T010 [US3] Extend `frontend/src/app/features/search/search.component.spec.ts`: assert the form is no longer width-pinned — e.g. the rendered `.ig-form--wide` element's effective `max-width` is `none`/`100%` (not `560px`/`420px`), or its width tracks the container at desktop viewport. Confirm the `.ig-grid2` two-column structure and the existing 520px stack breakpoint are intact, and all fields/validation/submit render. (contracts/ui-contracts.md UC-3.) NOTE: shares the file edited in T008 — run after T008, not in parallel with it.

### Implementation for User Story 3

- [X] T011 [US3] In `frontend/src/app/features/search/search.component.ts` styles (~line 124): change `.ig-form--wide { max-width: 560px; }` to an explicit override `.ig-form--wide { max-width: none; }` (or `100%`). Do NOT delete the rule — a global `.ig-form { ... max-width: 420px }` in `frontend/src/styles.css:143` would otherwise take over and make the form narrower. Keep `.ig-grid2` and the `@media (max-width: 520px)` stack rule. Verify no awkward zone between the form's 520px and the nav's 760px breakpoints. (research.md R3 CRITICAL correction; FR-008..FR-010.) NOTE: edits the same component file as T009 — sequence after US2, do not parallelize.

**Checkpoint**: All three stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verification and the mandatory review gate.

- [X] T012 Run `npm run build`, `npm test` (all specs incl. T002/T003/T008/T010 green), and lint in `frontend/`. If the runtime is unavailable, declare static-only and that US1-US3 are behaviorally unverified (quickstart.md).
- [~] T013 (NOT run as live-browser manual pass in this environment - no backend wired; equivalent acceptance behaviors are covered by automated DOM-level tests in app.component.spec.ts / search.component.spec.ts). Execute the manual checks in `specs/005-search-ui-fixes/quickstart.md`: authenticated nav, **throttled-reload no-flash (step 3a)**, live language toggle (грн/UAH + USD unchanged), and full-width form on desktop + mobile widths.
- [X] T014 Non-ASCII scan of any executed Windows script touched (expect NONE for this feature — confirm no `.ps1`/`.cmd`/`.bat` changed); the Cyrillic `грн` lives only in `*.json`/`*.ts` (UTF-8), which is allowed (Constitution V).
- [X] T015 MANDATORY Constitution-VI review: spin up at least two role sub-agents (front-end lead + QA) over the final diff, INCLUDING the build/parse/test result — not reading alone. Apply or explicitly report findings before marking done.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: none (empty).
- **User Stories (Phase 3-5)**: each depends only on Setup. US1 is fully independent of US2/US3.
  US2 and US3 share `search.component.ts` (+ its spec), so US3 follows US2 (not parallel).
- **Polish (Phase 6)**: after all targeted stories are done.

### User Story Dependencies

- **US1 (P1)**: independent — different files (`auth.service.ts`, `app.component.ts`). MVP.
- **US2 (P2)**: independent of US1; edits `search.component.ts` template + new `search.component.spec.ts`.
- **US3 (P3)**: independent of US1; edits `search.component.ts` styles + same spec → **must run after US2** to avoid same-file conflicts.

### Within Each User Story

- Tests first (T002/T003, T008, T010) — confirm they FAIL against current code, then implement.
- In US1: `auth.service.ts` token signal (T004) → auth status (T005) → `app.component.ts` neutral branch (T006) → consumer verification (T007).

### Parallel Opportunities

- US1 tests T002 (auth.service.spec.ts) and T003 (app.component.spec.ts) are different files → **[P]**.
- US1 (entirely) can proceed in parallel with US2 — disjoint files.
- US2 and US3 CANNOT run in parallel with each other (same component file + spec).
- T004 and T005 edit the same file (`auth.service.ts`) → sequential.

---

## Parallel Example: kicking off US1 tests

```bash
# Different files, no dependency — write both failing tests together:
Task: "T002 read-before-set reactivity test in frontend/src/app/core/auth/auth.service.spec.ts"
Task: "T003 no-flash TestBed test in frontend/src/app/app.component.spec.ts"
```

```bash
# Cross-story parallelism (different developers): US1 and US2 are disjoint files
Developer A: US1 (T002-T007)        # auth.service.ts, app.component.ts
Developer B: US2 (T008-T009)        # search.component.ts (template) + new spec
# Then US3 (T010-T011) after US2 — same component file
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 Setup (T001).
2. US1 (T002-T007): write failing reactivity + no-flash tests, then signal-back the token, add the
   tri-state status, wire the neutral nav branch, verify consumers.
3. **STOP and VALIDATE**: `npm test` green; logged-in nav correct, no guest-CTA flash on throttled
   reload. Ship the MVP.

### Incremental Delivery

1. Setup → baseline green.
2. US1 → test → ship (MVP: the P1 trust defect is fixed).
3. US2 → test → ship (currency localizes live).
4. US3 → test → ship (form fills the card).
5. Polish (T012-T015): full build/test/lint, manual quickstart, ASCII scan, mandatory two-role review.

---

## Notes

- [P] = different files, no incomplete dependency. US2/US3 are deliberately NOT [P] together.
- This is a frontend-only change: no money/payload/backend/LLM behavior changes (currency fix is
  label-only; auth fix changes only *when the nav re-renders*, not any balance value).
- Two CRITICAL pitfalls already caught in review and encoded above: (1) DO NOT delete the 560px cap
  (global 420px would win — override to `none`); (2) DO NOT settle for a post-login-only auth test
  (it passes while broken — use read-before-set / template render).
- Commit after each task or logical group; stop at any checkpoint to validate a story independently.
