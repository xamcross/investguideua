# Implementation Plan: Precious Metals Price Refresh

**Branch**: `011-precious-metals-prices` | **Date**: 2026-06-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/011-precious-metals-prices/spec.md`

## Summary

Collect PrivatBank gold and silver quotes daily (~10:00 Kyiv) from the public metals endpoint, persist
them in MongoDB, expose them admin-only, and ground the price of PRECIOUS_METALS options in the
existing investment-search flow with the exact stored value. The architecture mirrors feature 009
(military bonds): a scheduled scraper on GitHub Actions -> shared-secret machine-to-machine ingest ->
durable storage -> admin read. The one genuinely new piece is grounding: the advisor model emits a
`metal` discriminator on a precious-metals option, and the server backfills the exact sale rate per
gram (dropping the option if the metal is unknown or no price is stored). See
[research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/).

## Technical Context

**Language/Version**: Java 21 (backend), Node.js >= 20 (scraper), TypeScript / Angular 17+ (frontend)
**Primary Dependencies**: Spring Boot 3.x, Spring Data MongoDB, Jakarta Bean Validation; scraper uses
built-in `fetch` (no Playwright for metals - plain GET); Angular standalone components
**Storage**: MongoDB 7.x - new collection `metalPrices` (natural-key `_id` upsert, integer minor units)
**Testing**: JUnit 5 + Spring MockMvc (backend), Node built-in test runner (scraper), existing FE tests
**Target Platform**: Linux container backend (Fly.io, scales to zero); GitHub Actions Ubuntu runner for
the scheduled scraper; modern browsers for the SPA
**Project Type**: Web application (Angular frontend + Spring Boot backend) plus a standalone Node
scraper, scheduled by GitHub Actions
**Performance Goals**: Daily batch of ~50 records; admin read serves stored data directly; grounding
adds one indexed-`_id`-class lookup per metals option within an existing search
**Constraints**: Money in integer minor units (kopiykas) - no float; LLM server-side, catalog-grounded;
single backend + single MongoDB + managed LLM (no new infra); Windows-executed scripts pure ASCII
**Scale/Scope**: One new backend package (`metals`), ~2 changed files in `investment`, 1 scraper
script + shared-lib tweaks, 1 GitHub Actions workflow, small frontend additions

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0).

- [x] **I. MVP Discipline**: No new service/instance, queue, or scaling infra. One more MongoDB
      collection in the same database; new endpoints on the existing backend; the scheduled collector
      reuses the existing GitHub Actions pattern (not the backend). Scope limited to the clarified
      spec; no speculative extras.
- [x] **II. Fixed Stack**: Angular 17+/Java 21/Spring Boot 3.x/MongoDB 7 unchanged. No new backend
      dependency. The metals scraper uses Node's built-in `fetch` (no Playwright), so it adds **no**
      dependency and is lighter than the bond scraper. LLM stays behind `InvestmentAdvisorService`;
      no provider abstraction touched.
- [x] **III. LLM Guardrails**: LLM stays server-side and catalog-grounded. The prompt gains one
      optional `metal` enum field that is **server-validated** and drops the option on invalid/missing
      value or missing price - strengthening grounding. The price is never model-supplied. Output stays
      strict-JSON validated; token/temperature/rate caps and max-options unchanged.
- [x] **IV. Financial Integrity**: Metal rates stored and compared as integer minor units (kopiykas)
      per gram; conversion via BigInt with no float (`weightGrams` is a non-money dimension label, like
      bond yields, so it stays a double). No token-ledger changes. Result sets keep their disclaimers
      (no change to the disclaimer logic).
- [x] **V. Encoding & Verification**: No new Windows-executed scripts planned; the scraper `.mjs`,
      workflow `.yml`, and Java/TS sources are not Windows-executed (UTF-8/LF). Verification will be a
      non-ASCII scan of any touched `.ps1`/`.cmd`/`.bat` (expected none) plus `mvn test` and scraper
      `npm test`; static-only will be declared if a runtime is unavailable.
- [x] **VI. Multi-Role Review**: At least two role sub-agents (incl. an actual scan/parse/compile
      step) will review before this work is marked done.

**Result**: All gates pass. Complexity Tracking is empty (no deviations).

## Project Structure

### Documentation (this feature)

```text
specs/011-precious-metals-prices/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (ingest, read, grounding)
├── checklists/
│   └── requirements.md  # from /speckit.specify
└── tasks.md             # Phase 2 output (/speckit.tasks - NOT created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/investguide/
├── metals/                              # NEW package (parallels bonds/)
│   ├── MetalPrice.java                  # @Document("metalPrices"), composite-string @Id
│   ├── MetalPriceRepository.java        # MongoRepository<MetalPrice,String> + findFirstByMetalAndRateGroupOrderByWeightGramsAsc
│   ├── MetalPriceService.java           # ingest(batch) drop-and-count; currentSalePricePerGramMinor(metal); findAll
│   ├── MetalIngestAuth.java             # X-Metal-Ingest-Secret, constant-time, fail-closed
│   ├── MetalPriceIngestController.java  # POST /api/v1/admin/metal-prices
│   ├── MetalPriceController.java        # GET  /api/v1/metal-prices (ADMIN)
│   └── dto/{IngestMetalRequest,MetalPriceResponse,IngestResult}.java
├── config/
│   └── MetalsProperties.java            # prefix "metals": ingest.secret, primaryRateGroup
├── InvestGuideApplication.java          # EDIT: add MetalsProperties to @EnableConfigurationProperties
├── common/security/SecurityConfig.java  # EDIT: add metal ingest to PUBLIC_POST + ADMIN read matcher
└── investment/
    ├── InvestmentOption.java            # EDIT: add nullable Long metalPricePerGramMinor + String metal (trailing)
    ├── AdvisorOutputParser.java         # EDIT: 2-stage grounding - inject MetalPriceService; Stage-2 NON-throwing drop
    └── PromptBuilder.java               # EDIT: add metal to literal JSON schema block + enum-preservation rule

backend/src/main/resources/application.yml   # EDIT: metals.ingest.secret, metals.primaryRateGroup
.env.example                                  # EDIT: METAL_INGEST_SECRET

backend/src/test/java/com/investguide/metals/ # NEW tests mirroring bonds tests
├── MetalPriceIngestControllerTest.java
├── MetalPriceServiceTest.java
└── MetalPriceControllerTest.java
backend/src/test/java/com/investguide/investment/
├── AdvisorOutputParserTest.java              # EDIT: metals grounding + drop cases (see Test Plan)
└── PromptBuilderTest.java                    # EDIT: assert prompt instructs the metal field

scraper/
├── scrape-metals.mjs                    # NEW: plain-fetch collector -> ingest (status===true check)
├── lib/convert.mjs                      # EDIT: strip all Unicode whitespace before toMinorUnits
├── lib/post.mjs                         # EDIT: parameterize ingest path + header + secret-env name (default = bonds)
├── lib/validate.mjs                     # EDIT/ADD: validateMetalBatch (both metals present + floor)
├── lib/date.mjs (or shared)             # EDIT/NEW: extract toIsoDate (DD.MM.YYYY) from scrape-bonds.mjs
├── package.json                         # EDIT: add "scrape:metals" script
└── test/{convert.test.mjs, validate-metals.test.mjs, post.test.mjs}  # EDIT/NEW (post.test guards 009 regression)

frontend/src/app/core/investment/investment.models.ts   # EDIT: optional metal, metalPricePerGramMinor
frontend/src/app/features/search/results.component.ts    # EDIT: render price-per-gram fact row
frontend/src/assets/i18n/*                                # EDIT: results.pricePerGram label(s)

.github/workflows/refresh-metal-prices.yml   # NEW: daily cron 0 7 * * *, workflow_dispatch, concurrency group, METAL_INGEST_SECRET, BACKEND_BASE_URL prod, no Playwright steps
```

**Structure Decision**: Web application with a new self-contained backend `metals` package that
parallels `bonds` one-for-one, minimal surgical edits to the existing `investment` flow for grounding,
a new lightweight Node scraper reusing shared scraper libs, and a new scheduled workflow. This keeps
the new feed isolated (distinct secret/route) while reusing proven patterns.

> NOTE: The tree diagrams above are display-only and contain non-ASCII box-drawing characters. Do NOT
> paste them into a Windows-executed script (`.ps1`/`.cmd`/`.bat`) - doing so reintroduces the
> Windows-1252 corruption Constitution Principle V prevents.

## Test Plan (case enumeration for the net-new logic)

"Mirrors 009" covers the storage/ingest/read tests; these cases cover what does NOT exist in 009 and
must be named explicitly so `/speckit.tasks` generates them. Each maps to an FR/SC.

- **MetalPriceServiceTest** (mirrors BondPriceServiceTest, plus):
  - duplicate composite key within a batch keeps last occurrence; asserts `_id == "GOLD:one:2.5"` and
    that `2.5` does not fragment (FR-004a, FR-007, SC-002).
  - `metal` not in {GOLD,SILVER} rejected; negative/omitted rate rejected; valid records still stored
    with accurate accepted/rejected (FR-004, FR-010).
  - `currentSalePricePerGramMinor(GOLD)` with multiple weights+groups seeded returns the smallest
    `weightGrams` sale rate of the primary group; returns empty when none (FR-018, FR-019, SC-009).
- **MetalPriceIngestControllerTest** (mirrors BondPriceIngestControllerTest): valid secret 200; wrong
  secret 401; missing header 401; **blank server secret 401 fail-closed** (new code, name it); empty
  array 400; non-array 400; unknown field 400; partial batch tally (FR-008, FR-009, FR-010, SC-005).
- **MetalPriceControllerTest** (mirrors BondPriceControllerTest): ADMIN 200; empty store `200 []`;
  authenticated USER 403; anonymous 401 (FR-012, FR-013, SC-004).
- **AdvisorOutputParserTest** (edit): gold option with stored price gets exact `metalPricePerGramMinor`
  + `metal` (SC-009); metals option with missing `metal` dropped; metals option with no stored price
  dropped; **all-survivors-are-ungroundable-metals => empty result, NO exception/retry** (FR-019,
  SC-010); non-metals option returns `metal==null && metalPricePerGramMinor==null` (FR-020).
- **PromptBuilderTest** (edit): the built prompt instructs a `metal` (GOLD/SILVER) field for
  precious-metals options (guards the silent-drop trap).
- **convert.test.mjs** (edit): `"6 780.00" -> 678000`; NBSP (U+00A0) variant -> integer; `"100.75" ->
  10075`; `'1,076.58'` still throws (comma not stripped) (FR-005, SC-003).
- **validate-metals.test.mjs** (new): batch missing one metal rejected EVEN above the record floor;
  batch below floor rejected; valid both-metals batch passes (FR-015, SC-006).
- **post.test.mjs** (new): bond defaults unchanged (009 regression) and metals path/header/secret-name
  override works (FR-008).

## Complexity Tracking

> No constitution gate violations. Table intentionally empty.
