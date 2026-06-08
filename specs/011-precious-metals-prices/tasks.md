---
description: "Task list for feature 011 precious metals price refresh"
---

# Tasks: Precious Metals Price Refresh

**Input**: Design documents from `C:\Users\xamcr\InvestGuideUA\specs\011-precious-metals-prices\`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED - the spec/plan enumerate a Test Plan and Constitution Principles V/VI require an
actual parse/compile + test run before "done". Test tasks are written before (or alongside) the
implementation they cover and must fail first.

**Organization**: Grouped by user story. The shared backend storage core (entity, repository, DTOs,
service, auth) is Foundational because US1, US3, and US4 all depend on it; each story then adds only
its thin edge (controller, security matcher, scraper, or grounding integration).

**Path base**: backend `backend/src/main/java/com/investguide/`, backend tests
`backend/src/test/java/com/investguide/`, scraper `scraper/`, frontend `frontend/src/app/`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 / US2 / US3 / US4

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Config plumbing and package scaffolding shared by all stories.

- [x] T001 Create the backend `metals` package and `metals/dto` subpackage under `backend/src/main/java/com/investguide/metals/`
- [x] T002 Create `MetalsProperties` (`@ConfigurationProperties(prefix="metals")`, `ingest.secret`, `primaryRateGroup` default "one") in `backend/src/main/java/com/investguide/config/MetalsProperties.java`
- [x] T003 Register `MetalsProperties` in the explicit `@EnableConfigurationProperties` list in `backend/src/main/java/com/investguide/InvestGuideApplication.java` (no `@ConfigurationPropertiesScan` in this project)
- [x] T004 Add `metals.ingest.secret: ${METAL_INGEST_SECRET:}` and `metals.primary-rate-group: one` to `backend/src/main/resources/application.yml`
- [x] T005 [P] Add `METAL_INGEST_SECRET=` (with the fail-closed explanation) to `.env.example`

**Checkpoint**: Config binds at startup; backend still compiles and runs.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared MongoDB storage core + service + secret auth that US1 (read), US3 (ingest), and
US4 (grounding) all depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T006 [P] Create `MetalPrice` entity `@Document(collection="metalPrices")` with composite-string `@Id` (`<METAL>:<rateGroup>:<weightKey>`) and fields metal, rateGroup, weightKey, weightGrams (double), currency, purchaseRateMinor (long), saleRateMinor (long), quotationDate (LocalDate), fetchedAt (Instant) in `backend/src/main/java/com/investguide/metals/MetalPrice.java`
- [x] T007 [P] Create `IngestMetalRequest` record (bean validation: `@Pattern(GOLD|SILVER)` metal, `@NotBlank` rateGroup/weightKey, `@NotNull @Positive Double` weightGrams, `@Pattern(UAH)` currency, `@NotBlank` quotationDate ISO, `@NotNull @Min(0) Long` purchaseRateMinor/saleRateMinor) in `backend/src/main/java/com/investguide/metals/dto/IngestMetalRequest.java`
- [x] T008 [P] Create `MetalPriceResponse` record (mirrors entity fields) in `backend/src/main/java/com/investguide/metals/dto/MetalPriceResponse.java`
- [x] T009 [P] Create `IngestResult` record `(int accepted, int rejected)` in `backend/src/main/java/com/investguide/metals/dto/IngestResult.java`
- [x] T010 Create `MetalPriceRepository extends MongoRepository<MetalPrice,String>` with `findFirstByMetalAndRateGroupOrderByWeightGramsAsc(String metal, String rateGroup)` in `backend/src/main/java/com/investguide/metals/MetalPriceRepository.java` (depends on T006)
- [x] T011 [P] Create `MetalIngestAuth` (`HEADER="X-Metal-Ingest-Secret"`, constant-time `MessageDigest.isEqual`, fail-closed on blank, never logs secret) in `backend/src/main/java/com/investguide/metals/MetalIngestAuth.java` (depends on T002)
- [x] T012 Create `MetalPriceService` with `ingest(List<IngestMetalRequest>)` (per-record validate + drop-and-count, dedup by composite `_id` last-wins, single `fetchedAt`), `currentSalePricePerGramMinor(String metal)` (uses `metals.primaryRateGroup` + the derived smallest-weight query), and `findAll()` in `backend/src/main/java/com/investguide/metals/MetalPriceService.java` (depends on T006, T007, T010, T002)
- [x] T013 Write `MetalPriceServiceTest` in `backend/src/test/java/com/investguide/metals/MetalPriceServiceTest.java`: duplicate composite key within batch keeps last and `_id=="GOLD:one:2.5"` (FR-004a/007/SC-002); metal not in {GOLD,SILVER} / negative / omitted rate rejected while valid stored (FR-004/010); single `fetchedAt` per batch; `currentSalePricePerGramMinor` returns smallest-`weightGrams` sale rate of primary group and empty when none (FR-018/019/SC-009) (depends on T012)

**Checkpoint**: `mvn -q test` compiles and the service tests pass against the storage core.

---

## Phase 3: User Story 1 - Administrator views current metals prices (Priority: P1) 🎯 MVP

**Goal**: An ADMIN can read the stored gold/silver quotes; non-admins are denied.

**Independent Test**: Seed one `metalPrices` record, call `GET /api/v1/metal-prices` as ADMIN -> 200
with the record; as USER -> 403; anonymous -> 401; empty store -> `200 []`.

- [x] T014 [US1] Write `MetalPriceControllerTest` in `backend/src/test/java/com/investguide/metals/MetalPriceControllerTest.java`: ADMIN 200 with rows, empty store `200 []`, authenticated USER 403, anonymous 401 (FR-012/013, SC-004)
- [x] T015 [US1] Create `MetalPriceController` `GET /api/v1/metal-prices` mapping `repository.findAll()` to `MetalPriceResponse` (serves stored data only, no live fetch - FR-014) in `backend/src/main/java/com/investguide/metals/MetalPriceController.java`
- [x] T016 [US1] Add `.requestMatchers(HttpMethod.GET, "/api/v1/metal-prices").hasRole("ADMIN")` in `backend/src/main/java/com/investguide/common/security/SecurityConfig.java` (same file later touched by US3 - sequence, do not parallelize with T037)

**Checkpoint**: Admin read works end-to-end against seeded data; US1 independently demoable.

---

## Phase 4: User Story 2 - Automated daily refresh (Priority: P1)

**Goal**: A scheduled collector fetches the metals endpoint daily and ingests the parsed quotes; also
manually triggerable.

**Independent Test**: Run `npm run scrape:metals` with `METALS_DRY_RUN=true` and confirm a flattened,
correctly-converted batch (both metals, all groups/weights) is produced; with the secret set it warms
up and POSTs once and the stored records match the source.

- [x] T017 [US2] Extend `toMinorUnits` in `scraper/lib/convert.mjs` to strip ALL Unicode whitespace (JS `\s` + U+00A0/U+2007/U+2009/U+202F) from string inputs before the numeric regex; comma stays rejected (FR-005)
- [x] T018 [US2] Add cases to `scraper/test/convert.test.mjs`: `"6 780.00"->678000`, NBSP (U+00A0) variant, `"100.75"->10075`, and confirm `'1,076.58'` still throws (FR-005, SC-003)
- [x] T019 [US2] Extract `toIsoDate` (DD.MM.YYYY -> ISO yyyy-MM-dd) into `scraper/lib/date.mjs` and have `scrape-bonds.mjs` import it (no behavior change to bonds)
- [x] T020 [US2] Parameterize `warmupAndPost` in `scraper/lib/post.mjs` to accept `{ ingestPath, headerName, secretEnvName }` with bond values as defaults (so 009 stays byte-behavior-identical, incl. the "is not set" message)
- [x] T021 [US2] Create `scraper/test/post.test.mjs`: bond defaults unchanged (009 regression) and metals override uses `/api/v1/admin/metal-prices` + `X-Metal-Ingest-Secret` (FR-008)
- [x] T022 [US2] Add `validateMetalBatch` to `scraper/lib/validate.mjs`: require BOTH `GOLD` and `SILVER` present AND `METALS_MIN_RECORDS` floor (default 20); reject otherwise (FR-015)
- [x] T023 [US2] Create `scraper/test/validate-metals.test.mjs`: batch missing one metal rejected even above the floor; below-floor rejected; valid both-metals batch passes (FR-015, SC-006)
- [x] T024 [US2] Create `scraper/scrape-metals.mjs`: plain `fetch` of `https://privatbank.ua/pb/ajax/bank-metall-courses`, treat `status !== true` as failure, flatten gold+silver x rateGroup x weight into `IngestMetalRequest[]` (weightKey verbatim, `weightGrams=Number(weightKey)`, per-metal `date`->ISO, prices->minor units), validate, dry-run gate (`METALS_DRY_RUN`), then `warmupAndPost` to the metal route (depends on T017, T019, T020, T022)
- [x] T025 [US2] Add `"scrape:metals": "node scrape-metals.mjs"` to `scraper/package.json`
- [x] T026 [US2] Create `.github/workflows/refresh-metal-prices.yml`: `cron '0 7 * * *'`, `workflow_dispatch`, `concurrency` group, `BACKEND_BASE_URL` prod + `METAL_INGEST_SECRET` secret, Node 20, run `npm run scrape:metals` - NO Playwright cache/install steps (UTF-8/LF)

**Checkpoint**: Collector runs locally (dry-run) and, with US3's endpoint live, ingests end-to-end.

---

## Phase 5: User Story 4 - Metals options show the exact current price (Priority: P1)

**Goal**: A presented PRECIOUS_METALS option carries the exact stored sale-rate-per-gram; ungroundable
metals options are dropped without failing the search.

**Independent Test**: Unit-test `AdvisorOutputParser` with a mocked `MetalPriceService`: a gold option
gets the exact stored price; a metals option with missing `metal` or no stored price is dropped; an
all-metals-ungroundable result yields empty (no exception); a non-metals option is unchanged.

- [x] T027 [US4] Add trailing nullable `String metal` and `Long metalPricePerGramMinor` to the `InvestmentOption` record (boxed, so old persisted searches read null) in `backend/src/main/java/com/investguide/investment/InvestmentOption.java`
- [x] T028 [US4] Update `PromptBuilder` to add `metal` (GOLD/SILVER) to the literal JSON schema block AND the enum-preservation rule for precious-metals options in `backend/src/main/java/com/investguide/investment/PromptBuilder.java`
- [x] T029 [US4] Add a case to `backend/src/test/java/com/investguide/investment/PromptBuilderTest.java` asserting the built prompt instructs the `metal` field for precious-metals options (guards the silent-drop trap)
- [x] T030 [US4] Update `AdvisorOutputParser`: inject `MetalPriceService`; keep Stage-1 catalog grounding (the only throwing gate) unchanged; add Stage-2 NON-throwing post-filter that for PRECIOUS_METALS options validates `metal` and resolves `currentSalePricePerGramMinor`, sets both new fields, and drops (filters, never throws) on missing metal/price; non-metals options keep both null in `backend/src/main/java/com/investguide/investment/AdvisorOutputParser.java` (depends on T012, T027)
- [x] T031 [US4] Add grounding cases to `backend/src/test/java/com/investguide/investment/AdvisorOutputParserTest.java`: gold option gets exact `metalPricePerGramMinor`+`metal` and ignores any model price (SC-009); drop on missing `metal`; drop on no stored price; all-survivors-ungroundable-metals => empty result with NO exception/retry (FR-019, SC-010); non-metals option returns both null (FR-020)
- [x] T032 [P] [US4] Add optional `metal?: string` and `metalPricePerGramMinor?: number` to the `InvestmentOption` interface in `frontend/src/app/core/investment/investment.models.ts`
- [x] T033 [US4] Render a "price per gram" fact row (only when both fields present) in `frontend/src/app/features/search/results.component.ts` (depends on T032)
- [x] T034 [P] [US4] Add `results.pricePerGram` label(s) to the i18n files under `frontend/src/assets/i18n/`

**Checkpoint**: Metals options carry the exact grounded price; missing-data drops cleanly.

---

## Phase 6: User Story 3 - Secure machine-to-machine ingest (Priority: P2)

**Goal**: The collector submits parsed quotes via a distinct shared-secret endpoint; bad secret stores
nothing; malformed records are dropped and counted.

**Independent Test**: POST a batch with a valid `X-Metal-Ingest-Secret` -> 200 + accepted/rejected
tally and stored records; wrong/missing/blank-server secret -> 401 nothing stored; empty/non-array/
unknown-field -> 400.

- [x] T035 [US3] Write `MetalPriceIngestControllerTest` in `backend/src/test/java/com/investguide/metals/MetalPriceIngestControllerTest.java`: valid secret 200 + tally, wrong secret 401, missing header 401, blank server secret 401 (fail-closed - new code), empty array 400, non-array 400, unknown field 400, partial batch tally (FR-008/009/010, SC-005)
- [x] T036 [US3] Create `MetalPriceIngestController` `POST /api/v1/admin/metal-prices` (verify secret via `MetalIngestAuth` BEFORE parsing the raw `byte[]` body, fail-on-unknown-properties, return `IngestResult`) in `backend/src/main/java/com/investguide/metals/MetalPriceIngestController.java` (depends on T011, T012)
- [x] T037 [US3] Add `"/api/v1/admin/metal-prices"` to the `PUBLIC_POST` array in `backend/src/main/java/com/investguide/common/security/SecurityConfig.java` (same file as T016 - sequence after T016)

**Checkpoint**: Full pipeline live: scraper (US2) -> ingest (US3) -> storage -> admin read (US1).

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T038 [P] Runtime-verify the research assumption: `https://privatbank.ua/pb/ajax/bank-metall-courses` returns the expected JSON from a plain Node `fetch` (no anti-bot/CORS block); if it does not, revisit the no-Playwright decision (research R5)
- [ ] T039 Run quickstart.md validation (dry-run scrape, ingest, admin read, grounded search) per `specs/011-precious-metals-prices/quickstart.md`
- [x] T040 Verification gate (Constitution V): non-ASCII scan of any touched `.ps1`/`.cmd`/`.bat` (expect none here) returns clean; run `mvn -q test` (backend) and `npm test` (scraper) and report results, or declare static-only if a runtime is unavailable
- [x] T041 Mandatory multi-role sub-agent review (Constitution VI): at least two role sub-agents (e.g. BE lead + QA/DevOps), INCLUDING the scan/parse/compile step; apply or report findings before marking the feature done

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; BLOCKS US1, US3, US4 (and US2's live ingest).
- **US1 / US2 / US4 (P1)** and **US3 (P2)**: all depend on Foundational. They are independently
  testable but share two real couplings:
  - `SecurityConfig.java` is edited by both US1 (T016) and US3 (T037) - sequence, do not parallelize.
  - US4's parser (T030) depends on the Foundational `MetalPriceService` (T012).
  - US2's live end-to-end and the data US1/US4 read require US3's ingest endpoint (or a manual DB
    seed for isolated testing).
- **Polish (Phase 7)**: after the desired stories are complete.

### Recommended build order (vertical slice first)

Foundational -> **US3 (ingest)** -> **US1 (read)** = a working store-and-view pipeline, then **US2
(automate the refresh)**, then **US4 (user-facing grounding)**. (US1 is the spec MVP but only shows
data once US3 ingest or a manual seed has populated the store.)

### Parallel opportunities

- Setup: T005 [P].
- Foundational: T006, T007, T008, T009, T011 are [P] (different files); T010 then T012 then T013 are
  sequential.
- US4: T032 and T034 are [P] (frontend model + i18n); T033 follows T032; backend T027->T028/T030 chain.
- Across stories: once Foundational is done, US1, US2, and US4 backend/scraper work can proceed in
  parallel by different developers (mind the shared `SecurityConfig` between US1 and US3).

---

## Parallel Example: Foundational data structures

```bash
# After T001-T005, launch the independent storage building blocks together:
Task: "Create MetalPrice entity in backend/src/main/java/com/investguide/metals/MetalPrice.java"
Task: "Create IngestMetalRequest in backend/src/main/java/com/investguide/metals/dto/IngestMetalRequest.java"
Task: "Create MetalPriceResponse in backend/src/main/java/com/investguide/metals/dto/MetalPriceResponse.java"
Task: "Create IngestResult in backend/src/main/java/com/investguide/metals/dto/IngestResult.java"
Task: "Create MetalIngestAuth in backend/src/main/java/com/investguide/metals/MetalIngestAuth.java"
```

---

## Implementation Strategy

### MVP (storage-visible slice)

1. Phase 1 Setup + Phase 2 Foundational.
2. Phase 6 US3 (ingest) so data can flow, then Phase 3 US1 (read).
3. STOP and VALIDATE: scrape a batch (or seed), confirm admin read shows it.

### Incremental delivery

1. Foundational -> ingest + read pipeline (demo).
2. Add US2 -> automated daily refresh (demo).
3. Add US4 -> exact metals price inside investment options (the user-facing payoff).

### Notes

- [P] = different files, no incomplete dependencies.
- Money is integer minor units (kopiykas) everywhere; `weightGrams` is a non-money dimension label.
- Scraper `.mjs`, the workflow `.yml`, and Java/TS sources are NOT Windows-executed (UTF-8/LF); no new
  `.ps1`/`.cmd`/`.bat` is planned. Verification = non-ASCII scan (expect none) + `mvn test` + `npm test`.
- Verify tests fail before implementing; commit after each task or logical group.
