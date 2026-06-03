# Implementation Plan: Terms & Conditions and Privacy Statement Content

**Branch**: `004-terms-privacy-content` | **Date**: 2026-06-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-terms-privacy-content/spec.md`

## Summary

Replace the generic "coming soon" placeholder currently rendered at the public `/terms` and
`/privacy` routes with real, structured Terms & Conditions and Privacy Statement content. The
content is authored in both supported languages (Ukrainian canonical, English), follows the active
interface language live, shows an effective date, and accurately describes how InvestGuideUA
operates (information-only discovery, token-metered searches, AI-assisted catalog-grounded
recommendations, payments via monobank). Technical approach: a single standalone, OnPush
`LegalDocumentComponent` rendered by both routes via router component-input binding
(`data: { doc: 'terms' | 'privacy' }`), with all legal text stored as structured i18n content under
a new `legal.*` namespace in `public/i18n/uk.json` and `en.json`. No backend, no new dependency, no
new data store.

## Technical Context

**Language/Version**: TypeScript 5.x / Angular 17.3 (standalone components)  
**Primary Dependencies**: Angular Router (already configured with `withComponentInputBinding`),
`@ngx-translate/core` 15.x (runtime JSON dictionaries already wired). No new dependencies.  
**Storage**: N/A - legal text lives in the existing i18n JSON dictionaries
(`frontend/public/i18n/uk.json`, `en.json`); no database, no backend persistence.  
**Testing**: Existing frontend toolchain (Angular build / `ng build`, `ng test` where present);
static verification of JSON validity and a non-ASCII scan of any executed Windows scripts (none are
produced by this feature).  
**Target Platform**: Modern evergreen browsers; responsive 320px (small mobile) to large desktop.  
**Project Type**: Web application - this feature touches the Angular frontend only.  
**Performance Goals**: No measurable regression; content is static and lazy-loaded with the route.
Both screens interactive well under standard web expectations (sub-second on the already-loaded SPA).  
**Constraints**: All visible copy must be a translation key (no hardcoded UI strings); Ukrainian is
the default/canonical language; both screens stay public (no guard); content must render without
horizontal overflow or clipping at >=320px width.  
**Scale/Scope**: Two public screens, one shared component, two new i18n content namespaces
(`legal.terms`, `legal.privacy`), a two-line routing change. No change to auth, tokens, payments, or
the LLM path.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0).

- [x] **I. MVP Discipline**: No new service/instance, queue, or scaling infra; single backend +
      single MongoDB + managed LLM preserved. This feature is frontend-content only and adds no
      infrastructure, no new runtime moving parts, and no backend code. End-to-end deliverable
      (both screens fully populated), no half-wired stubs. **PASS**
- [x] **II. Fixed Stack**: Angular 17+/Java 21/Spring Boot 3.x/MongoDB 7 unchanged; no deprecated or
      EOL dependency added; provider access stays behind its abstraction. Reuses Angular standalone
      components and the already-pinned ngx-translate 15.x; **zero new dependencies**. **PASS**
- [x] **III. LLM Guardrails**: LLM calls remain server-side, catalog-grounded with server-side
      enforcement, token/temperature/rate caps applied, user text treated as data. **Not exercised**
      - this feature makes no LLM call. The authored Terms/Privacy text accurately *describes* the
      existing server-side, catalog-grounded LLM usage and must not over-claim. **PASS (N/A, with
      accuracy obligation)**
- [x] **IV. Financial Integrity**: money in integer minor units; token mutations are single-doc
      status-guarded ledger ops; balance cannot go negative; disclaimers attached. **Not exercised**
      - no money or token mutation occurs. The Terms content must carry/echo the
      information-not-advice disclaimer consistent with the platform-wide disclaimer rule. **PASS
      (N/A, with disclaimer-consistency obligation)**
- [x] **V. Encoding & Verification**: executed Windows scripts pure ASCII; verification is by scan +
      parse/compile (or explicitly declared static-only when no runtime is available), not by
      reading. This feature produces **no `.ps1`/`.cmd`/`.bat`**. Edited files are `.ts` and `.json`
      consumed by the Node/Angular build as UTF-8, so Ukrainian text is correct there. Verification
      plan: JSON parse-validate both dictionaries, `ng build` if a runtime is available (else
      declared static-only), and a non-ASCII scan confirming no Windows script was touched. **PASS**
- [x] **VI. Multi-Role Review**: at least two role sub-agents (incl. scan/parse) will review before
      this work is marked done. Planned reviewers: Frontend lead (Angular/i18n correctness, a11y,
      responsiveness) and QA/Business-Analyst (content completeness vs. FR-002/FR-004, bilingual
      parity, disclaimer/legal-accuracy), including JSON parse + build verification. **PASS
      (committed)**

**Result**: All gates pass. No violations; Complexity Tracking not required.

## Project Structure

### Documentation (this feature)

```text
specs/004-terms-privacy-content/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── legal-content.contract.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Web application; this feature changes the Angular **frontend only**. Relevant real directories:

```text
frontend/
├── src/
│   └── app/
│       ├── app.routes.ts                       # MODIFIED: /terms and /privacy point at the new
│       │                                       #   component with data: { doc: 'terms'|'privacy' }
│       └── features/
│           ├── legal/                          # NEW feature folder
│           │   └── legal-document.component.ts  # NEW: shared standalone, OnPush renderer
│           └── placeholder/
│               └── placeholder.component.ts     # UNCHANGED (still used by other pending routes)
└── public/
    └── i18n/
        ├── uk.json                             # MODIFIED: add legal.terms.* + legal.privacy.*
        │                                       #   (canonical Ukrainian) + title.terms/title.privacy
        └── en.json                             # MODIFIED: English parity for the same keys
```

**Structure Decision**: Reuse the existing Angular standalone + lazy-loaded route structure under
`frontend/src/app/features/`. Introduce one new feature folder `features/legal/` containing a single
shared `LegalDocumentComponent`. Both existing public routes (`/terms`, `/privacy`) load this one
component and select which document to render via route `data` bound to an `@Input() doc`
(component-input binding is already enabled app-wide). All copy is added to the existing i18n
dictionaries under a new `legal.*` namespace - no new asset-loading mechanism. This is the minimal
footprint that satisfies the spec (Constitution Principle I): one new component, two edited JSON
files, a two-line routes edit.

> NOTE: The tree diagrams above are display-only and contain non-ASCII box-drawing characters. Do
> NOT paste them into a Windows-executed script (`.ps1`/`.cmd`/`.bat`) - doing so reintroduces the
> Windows-1252 corruption that Constitution Principle V prevents.

## Complexity Tracking

> No Constitution violations. Table intentionally empty.
