# Implementation Plan: Landing marketing sections (footer, pricing preview, sample results)

**Branch**: `002-landing-marketing-sections` | **Date**: 2026-06-03 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-landing-marketing-sections/spec.md`

## Summary

Add three concept sections that the `001-ui-redesign` work deliberately left out (it restyled
existing screens only): a **sample-results ("Examples") section** and a **transparent-pricing
preview** on the public landing page, plus a **site footer** in the shell. All three are
marketing/informational, signed-out-facing surfaces built entirely from presentation: no new
business logic, no money math on live data, and no backend change. The sample options and the
pricing figures are **hard-coded, clearly-labelled illustrative content** (the same treatment as
the existing landing "trust ledger") because the authoritative `/tokens/packs` endpoint is
authenticated and must stay that way — exposing it to anonymous visitors is out of scope and a
security/MVP concern. The work reuses the `001-ui-redesign` design system (instrument-card and
dark-section patterns, motion, accessibility baseline, footer-ready navy tokens) and keeps all copy
in ngx-translate with `uk.json`/`en.json` identical. Verification is the production build (parse +
per-component style budget) plus the mandatory two-role sub-agent review.

## Technical Context

**Language/Version**: TypeScript 5.x / Angular 17+ (standalone, OnPush); CSS3. No backend change.
**Primary Dependencies**: Angular 17+, `@ngx-translate/core` (existing). **No new npm packages.**
**Storage**: N/A. No persisted data; sample/pricing figures are hard-coded display constants. Real
money paths (`money.util`, token ledger, `/tokens/packs`, payments) are untouched.
**Testing**: Production build (`npm run build`) as the parse + `anyComponentStyle` budget gate;
manual UK/EN runtime toggle; accessibility spot-checks (contrast, focus, reduced motion, keyboard);
mandatory two-role sub-agent review (FE lead + QA/accessibility) including an actual build.
**Target Platform**: Evergreen browsers, desktop + mobile (responsive single-column collapse).
**Project Type**: Web application (frontend-only change in the existing `frontend/` Angular app).
**Performance Goals**: No new network request on the public landing page (sample/pricing are static);
reduced-motion-aware entrance only; no behavior change to existing screens.
**Constraints**: Each component `styles[]` < 4 kb (warn) / 8 kb (error) — shared rules go in global
`styles.css`. Only `.ts/.css/.json/.html` touched (all UTF-8); no `.ps1/.cmd/.bat`. Illustrative
monetary figures MUST be clearly labelled as examples (Constitution IV guardrail). Footer legal
links MUST resolve (no broken links).
**Scale/Scope**: 1 new footer component + 2 new landing sections + 2 additive placeholder routes
(`/terms`, `/privacy`) + global `.ig-section--dark`/footer styles + i18n keys in both locales.
**Dependency**: builds on `001-ui-redesign` (its design system). This branch was cut from `main`
(pre-redesign); implementation assumes 001 is merged first (or 002 is rebased onto it). See
Complexity Tracking.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0). All gates pass.

- [x] **I. MVP Discipline**: No new service/instance, queue, or scaling infra. No backend change;
      the authenticated `/tokens/packs` endpoint is NOT exposed to anonymous users (rejected — see
      research). Only additive marketing UI + two additive placeholder routes. No existing-screen
      logic touched.
- [x] **II. Fixed Stack**: Angular 17+ standalone/OnPush/`ig-`-prefixed; **no new dependencies**;
      no deprecated libs. Backend (Java/Spring/Mongo) untouched.
- [x] **III. LLM Guardrails**: No LLM path involved. The sample-results cards use hard-coded
      illustrative data, NOT an advisor call — so no catalog-grounding/token-budget surface is
      touched and none is weakened.
- [x] **IV. Financial Integrity**: Real money paths (`money.util`, token ledger, packs, payments)
      are untouched. The pricing preview shows **illustrative, clearly-labelled** figures — not an
      authoritative quote and not derived from float math — with a disclaimer; it consumes no token
      and starts no purchase. Any minor-unit display follows the existing integer rules.
- [x] **V. Encoding & Verification**: Only `.ts/.css/.json/.html` edited (UTF-8, Cyrillic allowed);
      **no `.ps1/.cmd/.bat`**, so the ASCII-only Windows-script rule has nothing to flag. Verify by
      running the production build, not by reading; declare static-only if a runtime is unavailable.
- [x] **VI. Multi-Role Review**: Reviewed by at least two role sub-agents (FE lead +
      QA/accessibility) including an actual build before done.

**Post-Phase-1 re-check**: Design adds no dependency, no backend/security change, no money math on
live data. All gates remain green. No Complexity Tracking violations (the 001 dependency is a
sequencing note, not a constitutional deviation).

## Project Structure

### Documentation (this feature)

```text
specs/002-landing-marketing-sections/
├── plan.md              # This file
├── research.md          # Phase 0 - decisions (pricing source, footer scope, legal links, sample data)
├── data-model.md        # Phase 1 - display concepts + i18n key inventory
├── quickstart.md        # Phase 1 - build/verify + sequencing on top of 001
├── contracts/           # Phase 1 - UI contracts
│   ├── i18n-keys.contract.md
│   ├── footer.contract.md
│   ├── landing-sections.contract.md
│   └── preservation.contract.md
└── tasks.md             # Phase 2 (/speckit.tasks - NOT created here)
```

### Source Code (repository root)

Frontend-only. Touched/added files (assuming the `001-ui-redesign` design system is present):

```text
frontend/
├── src/
│   ├── styles.css                                   # global .ig-section--dark + footer/pack-preview shared rules
│   ├── app/
│   │   ├── app.component.ts                          # render <ig-footer/> beneath the router outlet
│   │   ├── app.routes.ts                             # ADD additive /terms + /privacy -> PlaceholderComponent
│   │   ├── core/layout/footer.component.ts           # NEW ig-footer (standalone, OnPush)
│   │   └── features/landing/landing.component.ts     # ADD sample-results + pricing-preview sections (+ display consts)
│   └── ...
└── public/i18n/
    ├── uk.json                                      # additive footer/sample/pricing keys
    └── en.json                                      # additive footer/sample/pricing keys (identical set)
```

**Structure Decision**: Existing single Angular app under `frontend/`. The footer is a new
shell-level standalone component (mirroring `core/errors/notification-host.component.ts`) rendered
once in `app.component`. The two landing sections live in the existing landing component, with shared
CSS promoted to global `styles.css` to stay under the per-component budget. `/terms` and `/privacy`
reuse the existing `PlaceholderComponent` via additive routes.

> NOTE: The tree diagrams above contain non-ASCII box-drawing characters and are display-only. Do NOT
> paste them into a Windows-executed script (`.ps1`/`.cmd`/`.bat`) - Constitution Principle V.

## Complexity Tracking

No constitution violations. One sequencing dependency (not a deviation) is recorded for the planner:

| Item | Why | Handling |
|------|-----|----------|
| Depends on `001-ui-redesign` | Reuses its design system (`.ig-opt`, dark-section, footer tokens, motion, a11y) | Land 002 after 001 merges, or rebase 002 onto 001 before implementing. This branch was cut from `main` for an independent spec diff. |
