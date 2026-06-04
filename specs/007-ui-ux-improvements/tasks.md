---
description: "Task list for Frontend UI/UX Audit & Improvements"
---

# Tasks: Frontend UI/UX Audit & Improvements

**Input**: Design documents from `C:\Users\xamcr\InvestGuideUA\specs\007-ui-ux-improvements\`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Unit specs ARE included - the design explicitly requests them (research.md D10;
each contract item carries a "Verify: unit spec ..." hook). Karma runs real headless Chrome,
so DOM/attribute/`getComputedStyle` assertions are reliable.

**Organization**: Tasks are grouped by the four user stories from spec.md so each story is an
independently testable increment. This is a presentation-layer-only feature - no backend, data,
money/token, or LLM code is touched (FR-021).

## Implementation status (2026-06-04, /speckit.implement)

**38 of 51 tasks complete.** US1-US4 implementation shipped; `npm run build` and `npm run test:ci`
are both green (54 unit tests pass, clean prod build, 12 routes prerendered). Two role sub-agents
(FE/accessibility lead + QA/DevOps) reviewed the diff and returned COMPLIANT / no blockers; the one
substantive finding (a non-functional `aria-busy` placement) was fixed and re-built.

**13 tasks deferred** (require an environment action a code edit cannot perform here, or are
follow-on coverage), each left unchecked and honestly tracked:

- **T001 (partial) + T003** - the `a11y:audit` npm script and the `axe-audit.mjs` harness are in
  place, but `@axe-core/puppeteer` was NOT added to `package.json`/`package-lock.json` because
  `npm install` could not be run here, and a stale lock would break the existing `npm ci` CI step.
  Remaining: `npm i -D @axe-core/puppeteer`, commit the lockfile, add the CI step. The harness
  lazy-imports the dep, so the build/CI stay green until then.
- **T038 (partial)** - radius literals tokenized (`--radius-xs`/`--radius-pill`); the broader
  spacing/type-scale sweep across component `styles[]` is not yet applied (purely internal
  consistency; no user-visible gap).
- **T005, T007, T008, T020, T041** - additional unit specs (skip link, search live region/lock,
  login/register aria + lock, touch-target computed-style). Two new spec files WERE added and pass
  (`route-focus.service.spec.ts`, `state-components.spec.ts`); these five are remaining coverage.
- **T019, T027, T040, T046, T049** - the run-the-axe-scan + manual keyboard/screen-reader/responsive
  verification gates. These need a running server + `@axe-core/puppeteer` (T001) and/or a human pass;
  they cannot be auto-completed in this environment.

Encoding (Principle V): no `.ps1/.cmd/.bat` added; `axe-audit.mjs` is pure ASCII (byte-scanned).
Regression (FR-023/SC-009): no money/format/i18n logic changed - additions are new i18n keys only.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 / US4 (maps to spec.md user stories)
- All paths are relative to repo root `C:\Users\xamcr\InvestGuideUA\`

## Heavily shared files (NOT parallel with each other)

These files are edited by many tasks across stories; edits to the same file MUST be sequential:

- `frontend/src/styles.css` - T004, T012, T015, T018, T021, T025, T026, T038
- `frontend/src/app/app.component.ts` - T009, T016, T022, T024
- `frontend/src/app/features/auth/login.component.ts` - T013, T042, T043, T045
- `frontend/src/app/features/auth/register.component.ts` - T014, T042, T045
- `frontend/src/app/features/search/search.component.ts` - T011, T037 (results wiring), T042, T044, T045
- `frontend/src/app/features/history/history.component.ts` - T012 (US1, focus-clip), T034 (US3, shared states)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the automated-scan tooling all stories verify against (no runtime deps).

- [ ] T001 [P] Add `@axe-core/puppeteer` (4.11.x) as a devDependency and an `"a11y:audit": "node tools/a11y/axe-audit.mjs"` script in `frontend/package.json` (reuses the headless Chrome already installed via `puppeteer` ^24 in `karma.conf.js`; no new runtime/client dependency)
- [X] T002 Create the automated WCAG scan harness `frontend/tools/a11y/axe-audit.mjs` (ASCII-only; Node ESM). It MUST: launch headless Chrome via `@axe-core/puppeteer` (reuse `puppeteer.executablePath()`), poll the target URL for readiness, navigate each PUBLIC route (`/`, `/search`, `/login`, `/register`, `/articles`, one sample article), run axe-core WCAG 2.1/2.2 A+AA rules, print violations, own browser teardown, and `process.exit(1)` on any violation. Do NOT scan authed/data routes (account, history, populated results) - they false-pass headlessly (research.md D1)
- [ ] T003 [P] Add an `a11y:audit` step to the frontend CI workflow (the same pipeline that runs `seo:audit`/`test:ci`), gating on its non-zero exit (mirrors the SEO gate; research.md D1, quickstart §3)

**Checkpoint**: `npm run a11y:audit` runs against a served app and can fail CI.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the shared design-scale vocabulary that US2/US3/US4 build on.

**CRITICAL**: T004 blocks the touch-target (US2), consistency (US3), and form (US4) work.

- [X] T004 Add canonical scales to `:root` in `frontend/src/styles.css` (ADDITIVE only; keep all existing tokens and the `--ig-*` aliases): spacing `--space-1..--space-8` (4/8/12/16/24/32/48/64px), type `--text-xs/.sm/.base/.lg`, `--radius-pill: 999px`, and `--touch-min: 44px` (data-model.md §1). Do not restyle anything yet - this only declares the vocabulary

**Checkpoint**: Token vocabulary available; user stories can begin (in parallel if staffed).

---

## Phase 3: User Story 1 - Accessible to keyboard & AT users (Priority: P1) 🎯 MVP

**Goal**: Every screen is perceivable and operable by keyboard/screen-reader users - skip link,
SPA route focus, results announcement, form error association, visible focus, reduced motion,
mobile-nav focus, contrast, and heading/landmark structure (FR-001..008).

**Independent Test**: `npm run a11y:audit` reports zero violations on public routes AND the
manual keyboard + screen-reader pass (quickstart §5/§6) completes the journeys with no A/AA
blocker.

### Tests for User Story 1

- [ ] T005 [P] [US1] Add spec in `frontend/src/app/app.component.spec.ts`: a visible-on-focus "skip to main content" link exists, is the first focusable element / precedes the header, and targets `#main-content`
- [X] T006 [P] [US1] Create `frontend/src/app/core/a11y/route-focus.service.spec.ts`: on a mocked Router `NavigationEnd`, focus is moved to the main landmark; on the server platform it is a no-op
- [ ] T007 [P] [US1] Extend `frontend/src/app/features/search/search.component.spec.ts`: the populated results region exposes `role="status"`/`aria-live="polite"` and `aria-busy="true"` while a search is in flight
- [ ] T008 [P] [US1] Extend `frontend/src/app/features/auth/login.component.spec.ts` and add `frontend/src/app/features/auth/register.component.spec.ts`: an invalid+touched field exposes `aria-invalid="true"` and `aria-describedby` pointing at its error element

### Implementation for User Story 1

- [X] T009 [US1] Add a visually-hidden-until-focus skip link as the first focusable element and `<main id="main-content" tabindex="-1">` in `frontend/src/app/app.component.ts` (reuse `.ig-sr-only`) (FR-001; contract C-A11Y-1)
- [X] T010 [US1] Create the SSR-safe `frontend/src/app/core/a11y/route-focus.service.ts` that focuses `#main-content` (or its first heading) on Router `NavigationEnd`, guarded by `isPlatformBrowser`; register it (e.g., inject + initialize in `app.component.ts` / `app.config.ts`) (FR-001; C-A11Y-2) (depends on T009 for the target; shares app shell so sequence after T009)
- [X] T011 [P] [US1] Add the polite live region + `aria-busy` to the results region in `frontend/src/app/features/search/search.component.ts`, announcing a concise result summary only on successful render so it does not double-fire with the existing assertive amount-field error (FR-003; C-A11Y-4; research.md D3)
- [X] T012 [US1] Fix focus-ring clipping: remove/adjust `overflow:hidden`/transform that clips the global `:focus-visible` ring (e.g., the history row) in `frontend/src/app/features/history/history.component.ts` and `frontend/src/styles.css` (FR-005; C-A11Y-5)
- [X] T013 [US1] Login form: add per-field validation + `aria-invalid` + `aria-describedby` to email/password in `frontend/src/app/features/auth/login.component.ts`, matching the search-form reference (FR-002; C-A11Y-3)
- [X] T014 [P] [US1] Register form: link the existing inline `.ig-error` spans via `aria-describedby` and set `aria-invalid` in `frontend/src/app/features/auth/register.component.ts` (FR-002; C-A11Y-3)
- [X] T015 [US1] Audit-and-confirm reduced motion: verify the global `@media (prefers-reduced-motion: reduce)` block in `frontend/src/styles.css` actually covers every component transition (history row, back-link icon, step scale); guard any outlier. Ignore the unused `.onscroll` mechanism (FR-004; C-A11Y-6; research.md D8) (shares styles.css - sequence with T012/T018)
- [X] T016 [US1] Mobile nav: ensure collapsed links are not focusable (`inert`/`hidden`/`tabindex`) and focus is neither trapped nor obscured when leaving the open menu in `frontend/src/app/app.component.ts` (FR-006; C-A11Y-10) (shares app.component.ts - sequence after T009)
- [X] T017 [P] [US1] Heading/landmark structure: ensure one `h1` per page, no skipped levels (fix landing nesting), and correct `header`/`nav`/`main`/`footer` landmarks in `frontend/src/app/features/landing/landing.component.ts` (+ any flagged page) (FR-008; C-A11Y-8)
- [X] T018 [US1] Contrast: measure the flagged pairs (disabled `.ig-btn` opacity .55, gold-on-light text, MODERATE risk badge, footer `--blue-300` on navy, pricing fine-print opacity-on-muted) and fix any below AA at the TOKEN level in `frontend/src/styles.css` - not per-use overrides (FR-007; C-A11Y-7; research.md D9)
- [ ] T019 [US1] Verify US1: run `npm run a11y:audit` (zero violations on public routes) and complete the keyboard/screen-reader + reduced-motion checklist in quickstart §5/§6 with no A/AA blocker

**Checkpoint**: US1 is fully functional and independently testable (this is the MVP).

---

## Phase 4: User Story 2 - Comfortable on phones & tablets (Priority: P2)

**Goal**: Every control is tappable (>=44px), no horizontal overflow 320-1440px, and grids
reflow without cramping (FR-009..012).

**Independent Test**: At 320/375/768/1024/1280/1440px no horizontal scroll/clipping; every
interactive control measures >=44x44px; the mobile menu closes on link tap (quickstart §7).

### Tests for User Story 2

- [ ] T020 [P] [US2] Add a spec (in `frontend/src/app/app.component.spec.ts`) asserting via `getComputedStyle` that the language toggle and the primary button class report an effective height >= 44px

### Implementation for User Story 2

- [X] T021 [US2] Apply the 44px floor on the shared control classes (`.ig-btn`, `.ig-field input/select/textarea`, `.ig-input`) using `min-height: var(--touch-min)` + adequate padding in `frontend/src/styles.css`, preserving the visual language (FR-009; C-A11Y-9) (depends on T004; shares styles.css - sequence with T025/T026)
- [X] T022 [US2] Bring the language toggle (currently 40px) and compact nav links to >=44px tappable area in `frontend/src/app/app.component.ts` (FR-009) (shares app.component.ts - sequence with T024)
- [X] T023 [P] [US2] Bring the toast dismiss and reference controls to >=44px in `frontend/src/app/core/errors/notification-host.component.ts` (FR-009)
- [X] T024 [US2] Confirm/fix mobile-menu link activation closes the menu and navigates in `frontend/src/app/app.component.ts` (FR-012; C-A11Y-10)
- [X] T025 [US2] Responsive overflow sweep: ensure long provider names and large money figures wrap (no horizontal scroll) down to 320px - apply `overflow-wrap`/`min-width:0` where needed across `frontend/src/app/features/**` and `frontend/src/styles.css` (FR-010; research.md D12)
- [X] T026 [US2] Add/adjust breakpoints where 560-900px multi-column forms/grids cramp (e.g. `.ig-grid2`, fact grids) in `frontend/src/styles.css` (FR-011; research.md D12)
- [ ] T027 [US2] Verify US2: axe target-size where applicable + the responsive/touch sweep in quickstart §7 across all six widths

**Checkpoint**: US1 + US2 both work independently.

---

## Phase 5: User Story 3 - Visually consistent & cohesive (Priority: P3)

**Goal**: One shared empty/loading/error pattern everywhere; spacing/type/radius from the named
scale; indicators consistent (FR-013..017).

**Independent Test**: Every data-driven screen shows empty/loading/error via the shared
components; no leftover ad-hoc `.ig-alert--info` empty state or unroled error; no stray
hardcoded `6px`/`16px` radii (quickstart §9).

### Tests for User Story 3

- [X] T028 [P] [US3] Add specs for the three shared components (`empty-state`, `loading-state`, `error-state`) asserting their ARIA roles (`loading-state` -> `role="status"`, `error-state` -> `role="alert"`) and that inputs/projected content render, in `frontend/src/app/features/shared/*.spec.ts`

### Implementation for User Story 3

- [X] T029 [P] [US3] Create `frontend/src/app/features/shared/empty-state.component.ts` (standalone; wraps `.ig-empty`; `code?`/`heading`/`message?` inputs + projected CTA slot) (FR-013; component-state-contract)
- [X] T030 [P] [US3] Create `frontend/src/app/features/shared/loading-state.component.ts` (standalone; `role="status"`; reduced-motion-safe indicator; `label` input) (FR-014)
- [X] T031 [P] [US3] Create `frontend/src/app/features/shared/error-state.component.ts` (standalone; `role="alert"`; `message` input + optional `retry` output with a >=44px control) (FR-015)
- [X] T032 [P] [US3] Refactor `frontend/src/app/features/providers/providers.component.ts` to use the shared empty/loading/error components (replaces `.ig-alert--info` empty)
- [X] T033 [P] [US3] Refactor `frontend/src/app/features/payments/tokens.component.ts` to use the shared empty/loading/error components - covers BOTH the pack-load error and the in-flight `buyError` branch
- [X] T034 [P] [US3] Refactor `frontend/src/app/features/history/history.component.ts` to use the shared loading/error components (keep its rich empty via `empty-state`)
- [X] T035 [P] [US3] Refactor `frontend/src/app/features/account/account.component.ts` to use the shared loading-state (profile fetch) AND error-state - the latter adds the currently-missing `role="alert"` (FR-014, FR-015)
- [X] T036 [P] [US3] Refactor `frontend/src/app/features/articles/articles-index.component.ts` empty `<p>` to the shared `empty-state`
- [X] T037 [US3] Refactor the search results no-options/error branches in `frontend/src/app/features/search/results.component.ts` (and `search.component.ts` wiring) to the shared empty/error components (sequence after US1 T011, which also edits the search results region)
- [ ] T038 [US3] Map ad-hoc spacing/type/radius literals to the scale tokens across `frontend/src/styles.css` and component `styles[]` (remove hardcoded `6px`/`16px` radii, collapse the `.82/.85/.88/.9/.92rem` font sprawl onto `--text-*`) (FR-016; data-model.md §1)
- [X] T039 [US3] Confirm status/risk/category indicators all resolve from the shared badge/chip classes + tokens (grep for inline color literals) and read consistently across screens (FR-017) (may touch styles.css - sequence after T038)
- [ ] T040 [US3] Verify US3: walk every data-driven screen through empty/slow/failed states and the consistency checks in quickstart §9

**Checkpoint**: US1 + US2 + US3 all independently functional.

---

## Phase 6: User Story 4 - Clear, forgiving form interactions (Priority: P3)

**Goal**: Required fields indicated, timely field-specific validation, helper text associated,
and in-flight value-locking (FR-018..020). Builds on US1's error association.

**Independent Test**: Each form (search, login, register, account) shows required indicators,
surfaces field-specific errors before submit, announces helper text, and locks fields during a
pending submit (quickstart §5 form items).

### Tests for User Story 4

- [ ] T041 [P] [US4] Add specs asserting: a visible required indicator is present; an invalid value on blur shows a field-specific message; and the form's controls are disabled while `submitting()` and re-enabled afterward, in `frontend/src/app/features/auth/login.component.spec.ts` and `register.component.spec.ts`

### Implementation for User Story 4

- [X] T042 [US4] Add visible required-field indicators with an accessible-name counterpart to required controls in `frontend/src/app/features/auth/login.component.ts`, `register.component.ts`, and `frontend/src/app/features/search/search.component.ts` (FR-018; C-A11Y-3) (shares login/register/search - sequence with T043/T044/T045)
- [X] T043 [US4] Make validation timely in `frontend/src/app/features/auth/login.component.ts`: format errors on blur, required errors on blur/submit (not only after a failed submit) (FR-019) (depends on T013)
- [X] T044 [US4] Associate the goals character-counter/hint with its textarea via `aria-describedby` in `frontend/src/app/features/search/search.component.ts` (FR-019; C-A11Y-3) (shares search.component.ts - sequence with T042/T045)
- [X] T045 [US4] Implement in-flight form locking in `login.component.ts`, `register.component.ts`, `frontend/src/app/features/search/search.component.ts`, and `frontend/src/app/features/payments/tokens.component.ts`: disable the whole field group while submitting, re-enable in a `finally`/error path, use `getRawValue()` for the payload (FR-020; C-A11Y-3b; research.md D11)
- [ ] T046 [US4] Verify US4: walk each form through empty/invalid/valid/submitting states per quickstart §5 form items

**Checkpoint**: All four user stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Regression safety, full verification, and the mandatory constitution gates.

- [X] T047 [P] Regression: confirm money/currency rendering (igCurrency pipe, return figures, token packs) is byte-identical and all translated strings unchanged; full UK<->EN switch shows no layout breakage from text-length shifts (FR-023/SC-009; quickstart §10). Also confirm via `git diff` of `frontend/src/styles.css` that the brand identity is preserved - existing brand color/typography tokens (navy/blue/gold ramps, font families) are UNCHANGED and only additive scales/fixes were made (FR-022/SC-009); and that no money/format logic changed
- [X] T048 Run the full quality gate: `cd frontend; npm run build` and `npm run test:ci` both green (parse/compile + unit, Constitution Principle V)
- [ ] T049 Run the complete verification: `npm run a11y:audit` (public routes, zero violations) + the full manual keyboard/SR/reduced-motion/responsive/contrast/consistency checklist across all 8 journeys (quickstart §3-§9)
- [X] T050 [P] Constitution Principle V gate: confirm NO `.ps1`/`.cmd`/`.bat` was added/changed and that `frontend/tools/a11y/axe-audit.mjs` is pure ASCII (`grep -rnP "[^\x00-\x7F]" frontend/tools/a11y/` returns no matches)
- [X] T051 MANDATORY multi-role sub-agent review (Constitution Principle VI): front-end lead + QA/accessibility + DevOps review the diff, INCLUDING an actual build/scan step (not reading only); apply or explicitly report findings before marking the feature done

> **SC-010 (deferred, post-ship)**: SC-010 (moderated usability study, >=5 participants, >=90%
> first-attempt mobile task success) is a UX-research activity, not an engineering task. It is
> intentionally NOT in this task list; schedule it as a separate post-deployment study once
> US1-US2 ship. T049's manual mobile pass is the engineering-side proxy until then.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies - start immediately.
- **Foundational (Phase 2 / T004)**: depends on Setup; BLOCKS US2, US3, US4 (US1 may begin in parallel - it does not need the new scales).
- **User stories (Phases 3-6)**: each depends on Foundational; US1 is the MVP. US4 depends on US1's form error-association (T013/T014) for its refinements.
- **Polish (Phase 7)**: depends on all targeted stories being complete.

### User story dependencies

- **US1 (P1)**: independent; only needs Setup (for verification) - can start before T004.
- **US2 (P2)**: needs T004 (`--touch-min`, scales). Independent of US1/US3/US4.
- **US3 (P3)**: needs T004 (scales). Independent of US1/US2; touches `results.component.ts` which US1 also touches (sequence T011 before T037).
- **US4 (P3)**: needs US1 T013/T014 (error association) before T043; otherwise independent.

### Within each story

- Tests (T005-T008, T020, T028, T041) are written first and should FAIL before implementation.
- Shared components (T029-T031) before the screen refactors that consume them (T032-T037).
- Token scaffolding (T004) before any task that maps values to tokens (T021, T038).

---

## Parallel Opportunities

### Setup

- T001 and T003 are [P] (different files); T002 depends on T001.

### User Story 1 (after Setup)

```text
# Tests together (distinct spec files):
T005 app.component.spec.ts | T006 route-focus.service.spec.ts | T007 search.component.spec.ts | T008 login/register specs
# Independent implementation (distinct files, safely parallel):
T011 search.component.ts | T014 register.component.ts | T017 landing.component.ts
# Sequential on shared files:
#   app.component.ts:  T009(skip link) -> T010(register route-focus service) -> T016(nav)
#   styles.css:        T012 -> T015 -> T018
#   login.component.ts: T013
```

### User Story 3 (after T004)

```text
# Create shared components in parallel:
T029 empty-state | T030 loading-state | T031 error-state
# Then refactor screens in parallel (distinct files):
T032 providers | T033 tokens | T034 history | T035 account | T036 articles-index
# T037 (results) is parallel WITHIN US3 but must come after US1's T011 if stories run concurrently
```

### Cross-story (if staffed)

- After T004: US2, US3, US4 can proceed concurrently by different developers; coordinate the
  shared-file edits listed at the top (styles.css, app.component.ts, login/search components).

---

## Implementation Strategy

### MVP first (User Story 1)

1. Phase 1 Setup (T001-T003) -> Phase 2 Foundational (T004).
2. Phase 3 US1 (T005-T019) - the accessibility MVP.
3. STOP and validate: `npm run a11y:audit` + manual keyboard/SR pass (T019).
4. Deploy/demo - the highest-impact, legally-relevant slice ships first.

### Incremental delivery

1. Setup + Foundational -> foundation ready.
2. US1 (a11y) -> validate -> ship (MVP).
3. US2 (touch/responsive) -> validate -> ship.
4. US3 (consistency) -> validate -> ship.
5. US4 (form UX) -> validate -> ship.
6. Polish (T047-T051) -> final regression + mandatory review.

### Notes

- [P] = different files, no incomplete dependency. Same-file tasks are sequential.
- Presentation-layer only: do not touch services/models/money/LLM (FR-021); regression-guard
  currency + i18n (T047).
- Commit after each task or logical group; stop at any checkpoint to validate a story.
- "Done" requires T048-T051: build + unit pass, ASCII scan clean, axe + manual checklist pass,
  and the two-role sub-agent review (Constitution V/VI).
