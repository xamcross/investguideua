# Implementation Plan: Search Page UI Fixes

**Branch**: `005-search-ui-fixes` | **Date**: 2026-06-04 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/005-search-ui-fixes/spec.md`

## Summary

Three frontend-only defects on the search page / shell, all isolated to the Angular SPA with
no backend, API, money, or LLM impact:

1. **Auth-aware navigation (P1)** — a logged-in user sees "Sign in"/"Register" instead of the
   account menu. Root cause: `AuthService.isAuthenticated` is a `computed()` over the
   **non-signal** private field `#accessToken`, so it captures no reactive dependency and stays
   frozen at its initial `false` even after `applySession` sets the token. The reactive
   `tokenBalance` (read from the `_user()` signal) updates, which is why the screenshot shows
   "5 tokens" beside guest CTAs. Fix: make authentication state reactive (back the access token
   with a signal and derive `isAuthenticated` from it). Plus, for the no-flash requirement
   (US1-5), add a tri-state auth status (`unknown | authenticated | guest`) so the shell nav —
   which renders before route guards and before the startup `refresh()` resolves — shows a neutral
   state until the session is known, then flips reactively. This makes `app.component.ts` change
   (neutral nav branch) in addition to `auth.service.ts`.
2. **Localized currency label (P2)** — the form's currency `<option>` text is hardcoded
   (`UAH`/`USD`) and bypasses the existing, already-wired `CurrencyLabelPipe` + `currency.*`
   translation keys (`uk.json` already maps `UAH -> грн`). Fix: render the option labels through
   the existing impure pipe so they localize live.
3. **Full-width form (P3)** — `.ig-form--wide { max-width: 560px }` (component-scoped) pins fields
   to the left of a wider card. **Note (from review):** a global rule `styles.css:143`
   `.ig-form { ... max-width: 420px }` also applies; the component-scoped 560px currently wins on
   specificity. Therefore the fix must **override** the cap (e.g. `.ig-form--wide { max-width: none }`
   or `100%`) so the existing two-column grid fills the card — it must NOT simply delete the 560px
   line, which would fall back to the global 420px and make the form *narrower*. Keep the existing
   mobile stack breakpoint.

Approach: minimal, reactivity-correct, reuse existing infrastructure (signals, ngx-translate,
the `CurrencyLabelPipe`). No new dependencies. The auth fix is regression-guarded by a
reactivity-aware unit test (see Testing note); the currency-label and form-width fixes need a new
`search.component.spec.ts` (none exists today) plus manual checks — see research.md and
quickstart.md.

## Technical Context

**Language/Version**: TypeScript 5.x on Angular 17+ (standalone components, signals)
**Primary Dependencies**: `@angular/core` (signals/`computed`), `@angular/forms`
(Reactive Forms), `@ngx-translate/core` (runtime i18n) — all already in use; none added
**Storage**: N/A (no backend, DB, or persisted-state change; access token remains in-memory only)
**Testing**: Karma + Jasmine (`ng test`); existing specs `auth.service.spec.ts`,
`auth.guards.spec.ts`, `results.component.spec.ts`. **No `search.component.spec.ts` exists** — one
must be created for the currency-pipe binding + form-width assertions. The `auth.service.spec.ts`
regression test must exercise **read-before-set** ordering (see Testing note below), because the
existing post-login assertions pass even with the non-reactive bug present
**Target Platform**: Web SPA, modern evergreen browsers (desktop + mobile viewports)
**Project Type**: Web application — frontend only for this feature (backend untouched)
**Performance Goals**: No change; purely presentational/reactivity fixes (no added network or
compute)
**Constraints**: Display-only currency change MUST NOT touch amounts or minor-unit math
(spec FR-007 / Constitution IV); accessibility of nav + form preserved; runtime language switch
must update labels without reload
**Scale/Scope**: 3 fixes across ~3 source files (`auth.service.ts`, `search.component.ts`, and
optionally `app.component.ts`), plus spec updates; no translation-file edits required (currency
keys already exist)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0).

- [x] **I. MVP Discipline**: No new service/instance, queue, or scaling infra. Single backend +
      single MongoDB + managed LLM untouched (backend not modified at all). Scope is three
      bounded UI fixes; no scope creep.
- [x] **II. Fixed Stack**: Angular 17+/standalone unchanged; no new, deprecated, or EOL
      dependency. Uses existing Angular signals and `@ngx-translate/core`. Provider/payment/LLM
      abstractions not touched.
- [x] **III. LLM Guardrails**: N/A — no LLM call is added or changed; the advisor flow and its
      server-side guardrails are not in scope.
- [x] **IV. Financial Integrity**: Currency change is **label-only** (FR-007). No amount,
      minor-unit, token-ledger, or balance logic is modified. `tokenBalance` continues to mirror
      the server-authoritative value; the auth-reactivity fix only corrects *when the nav
      re-renders*, not any balance value.
- [x] **V. Encoding & Verification**: No Windows-executed scripts (`.ps1`/`.cmd`/`.bat`) are
      created or modified, so the ASCII rule has no target here. The Cyrillic `грн` lives only in
      `*.json`/`*.ts` consumed by the Angular/Linux toolchain (UTF-8), which is permitted.
      Verification is by `ng build` + `ng test` + lint and a non-ASCII scan of any touched
      script (none expected) — not by reading. If the Node/Angular runtime is unavailable in the
      environment, results will be declared static-only.
- [x] **VI. Multi-Role Review**: At least two role sub-agents (front-end lead + QA, including a
      build/lint/parse step) will review before this work is marked done.

**Result**: PASS — no violations. Complexity Tracking left empty.

## Project Structure

### Documentation (this feature)

```text
specs/005-search-ui-fixes/
├── plan.md              # This file (/speckit.plan output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (UI state model; no DB entities)
├── quickstart.md        # Phase 1 output (run + manual verification)
├── contracts/
│   └── ui-contracts.md   # Phase 1 output (observable UI behaviors; no API change)
├── spec.md              # Feature specification
├── checklists/
│   └── requirements.md   # Spec quality checklist
└── tasks.md             # Phase 2 output (/speckit.tasks - NOT created here)
```

### Source Code (repository root)

```text
frontend/
├── src/
│   └── app/
│       ├── app.component.ts                       # CHANGED: shell + top nav. Reactive auth fix
│       │                                          #   makes the existing branch update; ALSO add
│       │                                          #   neutral state while auth status is `unknown`
│       │                                          #   (no-flash, US1-5)
│       ├── core/
│       │   ├── auth/
│       │   │   ├── auth.service.ts                # FIX 1: signal-backed access token; reactive
│       │   │   │                                  #   isAuthenticated; tri-state auth status
│       │   │   │                                  #   (unknown|authenticated|guest)
│       │   │   ├── auth.service.spec.ts           # regression MUST use read-before-set ordering
│       │   │   │                                  #   (existing post-login asserts pass even when
│       │   │   │                                  #   broken)
│       │   │   └── auth.guards.spec.ts            # unaffected; re-run to confirm no regression
│       │   └── i18n/
│       │       └── currency-label.pipe.ts         # reused as-is (already impure, already maps
│       │                                          #   UAH -> грн)
│       └── features/
│           └── search/
│               ├── search.component.ts            # FIX 2: option labels via igCurrency pipe;
│               │                                  #   FIX 3: override .ig-form--wide max-width to
│               │                                  #   none/100% (NOT delete - global 420px cap)
│               ├── search.component.spec.ts       # NEW: assert option text uses igCurrency +
│               │                                  #   form no longer width-capped
│               └── results.component.spec.ts      # unaffected; re-run
└── public/
    └── i18n/
        ├── uk.json                                # currency.UAH = "грн" (already present)
        └── en.json                                # currency.UAH = "UAH" (already present)
```

**Structure Decision**: Web application, frontend-only change. All edits live under
`frontend/src/app`. The backend (`com.investguide.*`) and MongoDB are not touched. The fix
reuses existing primitives: Angular signals for reactive auth state, the existing
`CurrencyLabelPipe` and `currency.*` translation keys for the label, and a CSS-only adjustment
for the form width. No new files in `src` are strictly required beyond test additions.

> NOTE: The tree diagrams above are display-only and contain non-ASCII box-drawing characters.
> Do NOT paste them into a Windows-executed script (`.ps1`/`.cmd`/`.bat`) - doing so reintroduces
> the Windows-1252 corruption that Constitution Principle V prevents.

## Complexity Tracking

> No Constitution violations. Section intentionally empty.
