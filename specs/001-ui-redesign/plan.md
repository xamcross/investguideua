# Implementation Plan: InvestGuideUA UI/UX Redesign

**Branch**: `001-ui-redesign` | **Date**: 2026-06-03 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-ui-redesign/spec.md`

## Summary

Restyle every existing Angular screen under one design language ("institutional trust + Ukrainian
identity") with zero changes to TypeScript logic, validators, services, routing, money math, or the
i18n mechanism. The approach consolidates all shared visual rules into the single global stylesheet
`frontend/src/styles.css` (tokens, fonts wiring, global `.ig-*` component classes, motion, focus,
reduced-motion) behind a back-compat alias layer so existing components keep compiling unchanged,
then restyles each component's inline `template`/`styles[]` to consume those globals while staying
under the production `anyComponentStyle` budget (4 kb warn / 8 kb error). The only behavioral
addition is a real responsive mobile navigation (a defect fix) and one tiny display-only currency
label pipe; all visible copy stays in ngx-translate with `uk.json`/`en.json` kept key-for-key
identical. Verification is by running the production build (parse + budget) plus the mandatory
two-role sub-agent review. The authoritative implementation reference is
[docs/UI-REDESIGN-SPEC.md](../../docs/UI-REDESIGN-SPEC.md).

## Technical Context

**Language/Version**: TypeScript 5.x / Angular 17+ (standalone components, OnPush); CSS3. Backend
(Java 21 / Spring Boot 3.x / MongoDB) is untouched by this feature.
**Primary Dependencies**: Angular 17+, `@ngx-translate/core` (existing), Google Fonts (Playfair
Display, Manrope, JetBrains Mono) via `<link>` only. **No new npm packages.**
**Storage**: N/A (no data-model or persistence change; money stays integer minor units, read-only).
**Testing**: Production build (`npm run build` / `ng build`) as the primary parse + style-budget
gate; manual UK/EN runtime toggle check; keyboard/contrast/reduced-motion accessibility spot-checks;
mandatory two-role sub-agent review including an actual build.
**Target Platform**: Modern evergreen browsers, desktop and mobile (responsive down to common phone
widths).
**Project Type**: Web application (frontend-only change within the existing `frontend/` Angular app).
**Performance Goals**: No invisible-text flash on font load (`display=swap`); restrained,
reduced-motion-aware animation; no perceptible interaction-behavior change versus today.
**Constraints**: Each component's `styles[]` MUST stay < 4 kb (warn) / 8 kb (error); shared rules
live only in global `styles.css`. Only `.html`/`.css`/`.ts`/`.json` files are edited (all UTF-8,
Cyrillic permitted); **no `.ps1`/`.cmd`/`.bat` touched**. `index.html` must not gain a BOM.
**Scale/Scope**: ~15 feature components + app shell + notification host + global stylesheet +
`index.html` + 2 i18n files + 1 new display pipe. ~13 distinct screens restyled across 5 build
phases (A foundation, then B-E).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0). All gates pass; no violations.

- [x] **I. MVP Discipline**: No new service/instance, queue, or scaling infra; deployment topology
      unchanged. Scope is a presentation-layer restyle of frozen screens plus one defect fix
      (mobile nav). No capability beyond spec scope.
- [x] **II. Fixed Stack**: Angular 17+/Java 21/Spring Boot 3.x/MongoDB 7 unchanged; **no new
      dependencies** (fonts via `<link>`, not npm). Components stay standalone, OnPush, `ig-`-prefixed.
      Provider/LLM/payment abstractions untouched.
- [x] **III. LLM Guardrails**: No LLM code path is modified; this is presentation only. Not
      applicable, and nothing in the feature weakens server-side LLM enforcement.
- [x] **IV. Financial Integrity**: Money stays integer minor units; `money.util`
      (`toMinorUnits`/`formatMinorUnits`) is read-only; the `igCurrency` pipe and all currency
      localization are display-only (label word only, no math). Disclaimers preserved (always-on +
      conditional currency-risk). Token-ledger logic untouched.
- [x] **V. Encoding & Verification**: Only `.html`/`.css`/`.ts`/`.json` edited - all UTF-8, Cyrillic
      allowed; **no `.ps1`/`.cmd`/`.bat` modified**, so the ASCII-only Windows-script rule has
      nothing to flag. `index.html` keeps no BOM. New i18n strings use ASCII punctuation. Verification
      is by running the production build (parse + budget), not by reading rendered text; if a runtime
      is unavailable it will be declared static-only.
- [x] **VI. Multi-Role Review**: Each phase will be reviewed by at least two role sub-agents
      (FE lead + QA/accessibility), including an actual build/parse, before being marked done.

**Post-Phase-1 re-check**: Design artifacts introduce no new dependency, service, money math, or
logic. All gates remain green. No Complexity Tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/001-ui-redesign/
├── plan.md              # This file
├── research.md          # Phase 0 output - design decisions
├── data-model.md        # Phase 1 output - display concepts + i18n key inventory
├── quickstart.md        # Phase 1 output - build/verify + phase order
├── contracts/           # Phase 1 output - UI contracts (tokens, classes, i18n, preservation)
│   ├── design-tokens.contract.md
│   ├── global-classes.contract.md
│   ├── i18n-keys.contract.md
│   └── preservation.contract.md
└── tasks.md             # Phase 2 output (/speckit.tasks - NOT created by /speckit.plan)
```

### Source Code (repository root)

Frontend-only feature. Touched files within the existing Angular app:

```text
frontend/
├── src/
│   ├── index.html                              # +4 font/preconnect lines (only change)
│   ├── styles.css                              # Phase A: tokens, atmosphere, headings,
│   │                                           #   global .ig-* classes, motion, focus
│   ├── app/
│   │   ├── app.component.ts                     # B: sticky topbar + responsive mobile menu
│   │   ├── core/
│   │   │   ├── errors/notification-host.component.ts   # B: toast restyle (markup/ARIA unchanged)
│   │   │   ├── i18n/currency-label.pipe.ts      # A: NEW display-only igCurrency pipe
│   │   │   └── investment/money.util.ts         # READ-ONLY (untouched)
│   │   └── features/
│   │       ├── landing/landing.component.ts      # C: editorial hero + trust ledger
│   │       ├── search/search.component.ts        # C: instrument-card form
│   │       ├── search/results.component.ts       # C: instrument cards; styles[] emptied
│   │       ├── payments/tokens.component.ts       # D: dark pack wall + recommended ribbon
│   │       ├── payments/payment-result.component.ts # D: state rail + spinner
│   │       ├── auth/login.component.ts            # D: credential card
│   │       ├── auth/register.component.ts         # D: credential card
│   │       ├── auth/verify.component.ts           # D: credential card
│   │       ├── history/history.component.ts        # E: editorial ledger + chips + pager
│   │       ├── history/history-detail.component.ts  # E: detail chrome (reuses ig-results)
│   │       ├── account/account.component.ts        # E: dossier ledger
│   │       ├── providers/providers.component.ts     # E: provider instrument cards
│   │       ├── not-found/not-found.component.ts      # E: on-brand empty state
│   │       └── placeholder/placeholder.component.ts  # E: on-brand empty state
│   └── ...
└── public/i18n/
    ├── uk.json                                  # additive keys + notFound.title copy change
    └── en.json                                  # additive keys + notFound.title copy change
```

**Structure Decision**: Existing single Angular web app under `frontend/`. No structural/layout
change to the repo; this feature edits only inline component `template`/`styles[]`, the global
stylesheet, `index.html`, the two i18n bundles, and adds one new pipe file. Build phases A-E map to
the spec's suggested phasing (Section 9 of the source doc).

> NOTE: The tree diagrams above are display-only and contain non-ASCII box-drawing characters. Do
> NOT paste them into a Windows-executed script (`.ps1`/`.cmd`/`.bat`) - doing so reintroduces the
> Windows-1252 corruption that Constitution Principle V prevents.

## Complexity Tracking

No constitution violations. Table intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none)    | -          | -                                    |
