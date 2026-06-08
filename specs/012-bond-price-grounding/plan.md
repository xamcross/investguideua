# Implementation Plan: Bond Price Grounding in Investment Options

**Branch**: `012-bond-price-grounding` | **Date**: 2026-06-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/012-bond-price-grounding/spec.md`

## Summary

Close the third stage for bonds (fetch -> persist -> **use as real price**): when the existing
investment-search generator presents a `MILITARY_BOND`/`GOV_BOND` option, ground it in the exact stored
PrivatBank quote for a specific bond. Option A (per-ISIN): the model picks an ISIN from a server-listed
set of stored bonds; the server backfills that bond's sell price + sell yield and drops the option if
the ISIN is unknown/unpriced. Pure grounding feature — reuses feature 009 `bondPrices` storage and the
merged feature 011 two-stage non-throwing grounding in `AdvisorOutputParser`. No new storage, scraper,
ingest endpoint, or workflow. See [research.md](research.md), [data-model.md](data-model.md),
[contracts/](contracts/).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript / Angular 17+ (frontend)
**Primary Dependencies**: Spring Boot 3.x, Spring Data MongoDB, Jackson; Angular standalone components
**Storage**: MongoDB 7.x — existing `bondPrices` collection (feature 009), read-only here; no new store
**Testing**: JUnit 5 + Spring MockMvc (backend), existing frontend tests
**Target Platform**: Linux container backend (Fly.io); modern browsers for the SPA
**Project Type**: Web application (Angular frontend + Spring Boot backend)
**Performance Goals**: Adds one ISIN `findById` lookup per bond option within an existing search, plus a
currency-filtered bond list rendered into the prompt within the fixed input-token budget
**Constraints**: Money in integer minor units (kopiykas); LLM server-side, catalog-grounded, hard
input-token budget preserved; no new infra; Windows-executed scripts pure ASCII
**Scale/Scope**: ~5 edited backend files, ~3 edited frontend files, test updates; no new package

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0).

- [x] **I. MVP Discipline**: No new service/instance/queue/scaling infra; no new collection. Reuses
      009 storage + 011 grounding; adds only fields, a prompt block, a parser branch, and read methods.
- [x] **II. Fixed Stack**: Angular 17+/Java 21/Spring Boot 3.x/MongoDB 7 unchanged; no new dependency;
      LLM stays behind `InvestmentAdvisorService`.
- [x] **III. LLM Guardrails**: LLM stays server-side and catalog-grounded. New `isin` field is
      server-validated and drops the option on invalid/unknown; bond price/yield are never
      model-supplied. The `ALLOWED_BONDS` list is rendered **inside** the existing deterministic
      input-token budget loop (bonds truncated before providers), so the hard `maxInputTokens` bound is
      preserved. Output stays strict-JSON validated; one corrective retry unchanged.
- [x] **IV. Financial Integrity**: `bondSellPriceMinor` is integer minor units (per 1000 face); the
      sell yield is a percentage (double, like existing yields/returns), clamped to `[0, maxReturnPct]`.
      No money-as-float. No token-ledger change; disclaimers unchanged.
- [x] **V. Encoding & Verification**: No Windows-executed scripts touched. Verification = non-ASCII scan
      (expected none) + `mvn test`; static-only declared if no runtime.
- [x] **VI. Multi-Role Review**: at least two role sub-agents (incl. scan/parse/compile) will review
      before done.

**Result**: All gates pass. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/012-bond-price-grounding/
├── plan.md            # This file
├── research.md        # Phase 0
├── data-model.md      # Phase 1
├── quickstart.md      # Phase 1
├── contracts/         # Phase 1 (investment-option-bond-grounding.md)
├── checklists/requirements.md
└── tasks.md           # Phase 2 (/speckit.tasks - NOT created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/investguide/
├── investment/
│   ├── InvestmentOption.java        # EDIT: + nullable String bondIsin, Long bondSellPriceMinor (trailing)
│   ├── AdvisorOutputParser.java     # EDIT: inject BondPriceService; bond branch in toOption (drop-on-missing; yield->expectedReturnPct)
│   └── PromptBuilder.java           # EDIT: inject BondPriceService; ALLOWED_BONDS block + isin schema/rule; bonds in the budget loop
└── bonds/
    └── BondPriceService.java        # EDIT: + findByIsin(String); + listForPrompt(String currency)

backend/src/test/java/com/investguide/
├── investment/AdvisorOutputParserTest.java     # EDIT: bond grounding cases (NO ctor calls here - drives parser via JSON)
├── investment/PromptBuilderTest.java           # EDIT: ALLOWED_BONDS + isin instruction + budget (providers survive)
├── investment/InvestmentSearchServiceTest.java # EDIT: the 2 `new InvestmentOption(` ctor sites (+ null, null)
└── bonds/BondPriceServiceTest.java             # EDIT: findByIsin / listForPrompt cases

frontend/src/app/core/investment/investment.models.ts   # EDIT: + bondIsin?, bondSellPriceMinor?
frontend/src/app/features/search/results.component.ts     # EDIT: bond price (per 1000 face) row + collapse min==max return to one number
frontend/public/i18n/{en,uk}.json                         # EDIT: results.bondPrice label (MUST say "per 1000 face value")
```

**InvestmentOption construction sites** (record gains 2 trailing fields; grep-confirmed 3 total
`new InvestmentOption(`): 1 production — `AdvisorOutputParser.toOption` — and 2 in
`InvestmentSearchServiceTest`. `AdvisorOutputParserTest` constructs none.

**Structure Decision**: Web application; this feature is a thin grounding extension at the same
enforcement point feature 011 used (`AdvisorOutputParser`) plus a prompt-list addition in
`PromptBuilder` and two read methods on the existing `BondPriceService`. No new package, endpoint, or
storage.

> NOTE: The tree diagrams above are display-only and contain non-ASCII box-drawing characters. Do NOT
> paste them into a Windows-executed script (`.ps1`/`.cmd`/`.bat`).

## Test Plan (case enumeration for the net-new logic)

- **AdvisorOutputParserTest** (edit):
  - bond option with a stored, currency-matching ISIN gets `bondIsin` + `bondSellPriceMinor` and
    `expectedReturnPct.min==max==clamp(sellYield)` (SC-001/FR-003);
  - **model-supplied price/return ignored**: model emits its own `expectedReturnPct{14,16}` (and a bogus
    price field) -> option carries `min==max==storedYield` and `bondSellPriceMinor` ==
    `isEqualTo(storedSellPriceMinor)` exactly (FR-010/SC-006);
  - missing `isin` dropped; unknown/unpriced ISIN dropped (FR-004);
  - **currency mismatch** (stored bond currency != resolved option currency) dropped (FR-011/SC-007);
  - all-bond-ungroundable => empty, NO exception/retry (FR-005/SC-003);
  - **mixed valid ISINs**: two bond options name two different stored ISINs -> each carries its own
    values, no cross-contamination (spec US1 scenario 4);
  - non-bond option keeps bond fields null and its own return range (FR-006/SC-004);
  - sky-high stored yield clamped to `maxReturnPct`.
- **PromptBuilderTest** (edit): the prompt contains an `ALLOWED_BONDS` block (in the userPrompt) listing
  stored ISINs filtered to the request currency and instructs the `isin` field; with many bonds the
  whole prompt stays within `maxInputTokens` AND the first provider (`slug=prov-0`) still survives while
  bond lines are dropped (proves bonds-truncated-before-providers, not just total<=budget); empty bond
  set renders the "no bond options" instruction.
- **BondPriceServiceTest** (edit): `findByIsin` returns the stored record / empty; `listForPrompt`
  returns only the requested currency (and excludes other currencies incl. EUR).
- **InvestmentSearchServiceTest** (edit): update the two `new InvestmentOption(...)` sites for the two
  new trailing args (`null, null`).
- **Frontend (results.component)**: a grounded bond option renders the per-1000-face price row and shows
  the return as a single number when `min==max` (not "X-X"); a non-bond option is unchanged (FR-012).
- **Structural (no new test, stated explicitly)**: FR-007 (no live fetch) is guaranteed by using
  read-only `findByIsin`; FR-009/SC-005 (admin-only raw reads) are unchanged — this feature does not
  touch `BondPriceController`, so the existing feature-009 `BondPriceControllerTest` still covers it.

## Complexity Tracking

> No constitution gate violations. Table intentionally empty.
