# Implementation Plan: Frontend UI/UX Audit & Improvements

**Branch**: `007-ui-ux-improvements` | **Date**: 2026-06-04 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-ui-ux-improvements/spec.md`

## Summary

Harden the existing Angular frontend's presentation layer to WCAG 2.2 AA, mobile/touch comfort, and design-system consistency without changing any business behavior. The approach: (1) systematize the existing design tokens (add explicit spacing/type/radius scales and a 44px touch-target floor), (2) close specific accessibility gaps the audit found (skip link + route focus, search-results live region, login/register form error association including in-flight value-locking, focus-ring clipping, contrast verification), (3) unify empty/loading/error states behind shared presentational components, and (4) verify with an automated axe scan (built on the headless Chrome already installed via the existing `puppeteer` dev dependency) plus a documented manual keyboard/screen-reader pass. All work is presentation-layer only; no backend, data, money/token, or LLM code is touched.

## Technical Context

**Language/Version**: TypeScript 5.4, Angular 17.3 (standalone components), HTML, CSS
**Primary Dependencies**: `@angular/{core,common,forms,router,ssr,platform-server}` ^17.3, `@ngx-translate/core` ^15, RxJS 7.8. Proposed dev-only addition: `@axe-core/puppeteer` (latest 4.11.x, peer `puppeteer >=1.10.0`, satisfied by the installed `puppeteer` ^24 that `karma.conf.js` already uses to provide headless Chrome).
**Storage**: N/A - presentation-only feature; no persistence, no data model changes.
**Testing**: Karma + Jasmine unit specs (existing) for component a11y attributes/behavior; a Node ESM audit script under `frontend/tools/a11y/` that *launches* headless Chrome via `@axe-core/puppeteer` and runs axe-core against the live, served, hydrated DOM (this is a browser-driving script - unlike the existing `tools/seo/seo-audit.mjs`, which statically reads prerendered HTML off disk and launches no browser); a documented manual keyboard + screen-reader checklist for the authoritative AA bar.
**Target Platform**: Evergreen browsers, server-side-rendered + browser hydration; viewport range 320px to 1440px; Ukrainian (default) / English bilingual.
**Project Type**: Web - frontend only for this feature (no backend changes).
**Performance Goals**: No visual-stability regression (no new layout shift); reduced-motion fully honored; no measurable increase in bundle size beyond trivial CSS/markup. Token (currency) display and money formatting must render identically.
**Constraints**: Presentation layer only (FR-021); preserve the "institutional trust + Ukrainian identity" design language, colors, and typography (FR-022); all bilingual content and language switching unchanged with no length-shift breakage (FR-023); 44x44 CSS px minimum touch targets; SSR-safe (guard all `document`/`window` access).
**Scale/Scope**: ~20 standalone components across landing, search/results, providers, tokens, auth (login/register/verify), account, history, articles, legal, not-found; one global stylesheet (`frontend/src/styles.css`); 8 primary journeys to verify.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0). All gates pass.

- [x] **I. MVP Discipline**: No new service/instance, queue, or scaling infra. Single backend + single MongoDB + managed LLM topology untouched (this feature changes only frontend presentation). Scope is bounded by the spec and stays within frozen product scope - no new product capabilities.
- [x] **II. Fixed Stack**: Angular 17+/standalone unchanged; no framework substitution; no deprecated/EOL dependency. The only proposed addition is the dev-only, currently-supported `@axe-core/puppeteer` (4.11.x, peer `puppeteer >=1.10.0`) testing helper layered on the headless Chrome the existing `puppeteer` ^24 dev dependency already installs (used today by `karma.conf.js`) - a test tool, not a runtime dependency, and not a stack change. No new runtime dependency ships to the client bundle.
- [x] **III. LLM Guardrails**: Not applicable and not weakened - no LLM call path, prompt, or server code is modified. The Anthropic key path is untouched.
- [x] **IV. Financial Integrity**: Not applicable and not weakened - no money/token logic changes. Money remains integer minor units; the currency-display pipe and disclaimers are preserved verbatim (regression-checked under FR-023/SC-009). No ledger code touched.
- [x] **V. Encoding & Verification**: No new Windows-executed scripts (`.ps1`/`.cmd`/`.bat`). The audit helper is a Node ESM `.mjs` (read by Node as UTF-8, same as existing `tools/seo/*.mjs`). Verification is by build/lint/unit-test runs + the axe scan, not by reading. Any doc/source touched is scanned for stray non-ASCII before "done".
- [x] **VI. Multi-Role Review**: At least two role sub-agents (front-end lead + QA/accessibility, plus DevOps where the audit script is involved) will review before this work is marked done, including an actual build/parse/scan step.

**Result**: PASS - no violations, Complexity Tracking not required.

## Project Structure

### Documentation (this feature)

```text
specs/007-ui-ux-improvements/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output - technical decisions
├── data-model.md        # Phase 1 output - design-token scales + screen/state inventory
├── quickstart.md        # Phase 1 output - how to build, scan, and manually verify
├── contracts/           # Phase 1 output - per-component a11y/interaction contracts
│   ├── accessibility-contract.md
│   └── component-state-contract.md
├── checklists/
│   └── requirements.md  # Already created by /speckit.specify
└── tasks.md             # Phase 2 output (/speckit.tasks - NOT created here)
```

### Source Code (repository root)

This feature touches the frontend only. Affected real paths:

```text
frontend/
├── src/
│   ├── styles.css                       # Global design system: add spacing/type/radius
│   │                                    #   scales, 44px touch floor, focus-clip fixes,
│   │                                    #   shared empty/loading/error utilities
│   ├── index.html                       # Verify viewport meta (already correct)
│   └── app/
│       ├── app.component.ts             # Skip link + #main-content target; nav touch
│       │                                #   targets; mobile-menu focus/dismiss behavior
│       ├── core/
│       │   ├── a11y/                     # NEW: route-focus service (move focus to main
│       │   │   └── route-focus.service.ts#   on SPA navigation), SSR-safe
│       │   ├── layout/footer.component.ts
│       │   └── errors/notification-host.component.ts  # toast control touch targets
│       └── features/
│           ├── shared/                   # NEW: shared presentational state components
│           │   ├── empty-state.component.ts
│           │   ├── loading-state.component.ts
│           │   └── error-state.component.ts
│           ├── auth/login.component.ts    # add per-field validation + aria association
│           ├── auth/register.component.ts # link inline errors via aria-describedby
│           ├── search/search.component.ts # results-region aria-live announcement
│           ├── search/results.component.ts
│           ├── providers/providers.component.ts   # adopt shared empty/loading/error
│           ├── payments/tokens.component.ts        # adopt shared empty/loading/error
│           ├── history/history.component.ts        # focus-ring clip fix; shared states
│           ├── account/account.component.ts        # error alert role
│           └── articles/articles-index.component.ts# adopt shared empty state
└── tools/
    └── a11y/                            # NEW: automated WCAG scan (mirrors tools/seo/)
        └── axe-audit.mjs
```

**Structure Decision**: Single existing Angular SPA (`frontend/`); no new project or module boundary is introduced. New code is limited to (a) a small `core/a11y` focus service, (b) three shared presentational state components under `features/shared`, and (c) a `tools/a11y` audit script - all additive and presentation-scoped. The backend project is not part of this feature's structure.

> NOTE: The tree diagrams above are display-only and contain non-ASCII box-drawing
> characters. Do NOT paste them into a Windows-executed script (`.ps1`/`.cmd`/`.bat`) -
> doing so reintroduces the Windows-1252 corruption that Constitution Principle V prevents.

## Complexity Tracking

> No Constitution violations. The only net-new dependency is the dev-only
> `@axe-core/puppeteer` testing helper, which is justified under Principle II (current,
> supported, test-scoped, reuses the existing puppeteer dev dependency) and therefore is
> not a deviation requiring justification here.

## Phase 0: Research

See [research.md](./research.md). The spec carries no open `[NEEDS CLARIFICATION]` markers; research resolves the *implementation* decisions (automated-scan tooling, skip-link/route-focus pattern, live-region strategy, touch-target technique, design-scale tokenization, Angular reactive-forms error-association pattern, shared-state component design, reduced-motion verification scope).

## Phase 1: Design & Contracts

- [data-model.md](./data-model.md): the design-token scales (spacing, type, radius) and the screen x state inventory matrix that drives the consistency work (this feature's structured "model").
- [contracts/accessibility-contract.md](./contracts/accessibility-contract.md): the per-surface accessibility contract (skip link, focus, live regions, form association, contrast, reduced motion) used as acceptance criteria.
- [contracts/component-state-contract.md](./contracts/component-state-contract.md): the shared empty/loading/error component contracts and which screens adopt them.
- [quickstart.md](./quickstart.md): build/serve, run the automated axe scan, and the manual keyboard + screen-reader + responsive verification checklist.
