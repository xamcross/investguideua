# Tasks: Military Bond Price Refresh

**Input**: Design documents from `/specs/009-military-bond-prices/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md
**Branch**: `009-military-bond-prices`

**Tests**: INCLUDED. The constitution (Principles V & VI) makes `mvn -B test` / `node --test` and a
verifying review a hard gate, and plan.md enumerates test tasks. So test tasks are first-class here.

**Organization**: Tasks are grouped by user story (US1/US2/US3 from spec.md) so each story can be
implemented and tested independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 (setup, foundational, and polish tasks carry no story label)
- Exact file paths are absolute-from-repo-root.

## Path Conventions

Web service repo: backend Java under `backend/src/main/java/com/investguide/`, tests under
`backend/src/test/java/com/investguide/`. New Node scraper under `scraper/`. CI under
`.github/workflows/`. Windows helper under `scripts/` (pure ASCII per Constitution V).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the new package/project skeletons and config placeholders.

- [X] T001 [P] Create the backend bonds package with a Javadoc `package-info.java` (purpose: bond
      price ingest + read; mirrors `catalog/package-info.java` style) in
      `backend/src/main/java/com/investguide/bonds/package-info.java`
- [X] T002 [P] Scaffold the Node scraper project: `scraper/package.json` with `"type":"module"`, a
      PINNED `playwright` dependency on a current release line, and `"scripts": { "test": "node --test" }`
- [X] T003 [P] Add a commented `BOND_INGEST_SECRET=` placeholder to `.env.example` under a clearly
      marked "feature 009 / bond ingest" section (consistent with how `MONO_TOKEN`/`MAIL_*` are shown)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared config + the `BondPrice` entity/repository that BOTH the read (US1) and ingest
(US3) stories depend on.

**⚠️ CRITICAL**: No user-story work can begin until this phase is complete.

- [X] T004 [P] Add `bonds.ingest.secret: ${BOND_INGEST_SECRET:}` (blank default; SECRET, env only,
      never logged) to `backend/src/main/resources/application.yml` with a comment that blank => ingest
      fails closed, app still starts
- [X] T005 [P] Create `BondsProperties` as a `@Validated @ConfigurationProperties("bonds")` record
      with a **blank-tolerant** `Ingest(String secret)` (NOT `@NotBlank`) in
      `backend/src/main/java/com/investguide/config/BondsProperties.java`
- [X] T006 Register `BondsProperties.class` in the `@EnableConfigurationProperties({...})` allowlist
      in `backend/src/main/java/com/investguide/InvestGuideApplication.java` (no scanning exists)
      (depends on T005)
- [X] T007 [P] Create the `BondPrice` entity `@Document("bondPrices")`: `isin` as `@Id` (implicit
      unique), `boolean military`, `String currency`, `LocalDate maturity`/`quotationDate`,
      `long sellPriceMinor`/`buyPriceMinor`, `double sellYield`/`buyYield`, `Instant fetchedAt`, in
      `backend/src/main/java/com/investguide/bonds/BondPrice.java`
- [X] T008 Create `BondPriceRepository extends MongoRepository<BondPrice, String>` in
      `backend/src/main/java/com/investguide/bonds/BondPriceRepository.java` (depends on T007)

**Checkpoint**: Config + persistence foundation ready — US1 and US3 can proceed.

---

## Phase 3: User Story 1 - Administrator views current military bond prices (Priority: P1) 🎯 MVP

**Goal**: An ADMIN can read all stored bond price records; non-admins are denied; empty store -> `[]`.

**Independent Test**: Seed one `bondPrices` document, call `GET /api/v1/bond-prices` as ADMIN -> 200
with the row; as a non-admin -> 403; anonymous -> 401; with an empty collection -> `200 []`.

### Tests for User Story 1

> Write first; expect FAIL until T010-T012 land.

- [X] T009 [P] [US1] `BondPriceControllerTest`: ADMIN -> 200 with rows; authenticated non-admin -> 403;
      anonymous -> 401; empty collection -> `200 []`; assert prices are integer minor units, in
      `backend/src/test/java/com/investguide/bonds/BondPriceControllerTest.java`

### Implementation for User Story 1

- [X] T010 [P] [US1] Create `BondPriceResponse` read DTO (all entity fields incl. `fetchedAt`;
      `from(BondPrice)` factory) in `backend/src/main/java/com/investguide/bonds/dto/BondPriceResponse.java`
- [X] T011 [US1] Implement `BondPriceController` `GET /api/v1/bond-prices` returning all rows mapped to
      `BondPriceResponse` (read-only; never triggers a scrape) in
      `backend/src/main/java/com/investguide/bonds/BondPriceController.java` (depends on T008, T010)
- [X] T012 [US1] Add `.requestMatchers(HttpMethod.GET, "/api/v1/bond-prices").hasRole("ADMIN")` to
      `backend/src/main/java/com/investguide/common/security/SecurityConfig.java` (mirror the existing
      `/api/v1/providers` rule)

**Checkpoint**: US1 fully functional and independently testable (seed -> read as ADMIN).

---

## Phase 4: User Story 2 - Automated daily refresh keeps prices current (Priority: P1)

**Goal**: A headless-browser scraper collects PrivatBank bond quotes, converts to minor units, and
POSTs them; runs on a business-day GitHub Actions cron and locally, and fails loud without blanking
data.

**Independent Test**: Run `node --test` for the conversion; run the scraper in a dry/local run and
confirm it intercepts the XHR, validates (min-record floor + schema), and on success POSTs a batch
that the read endpoint then shows. (Full end-to-end POST requires US3's ingest endpoint — see
Dependencies; the conversion/validation/interception slices are independently testable.)

### Tests for User Story 2

- [X] T013 [P] [US2] `scraper/test/convert.test.mjs` (`node --test`): assert `1076.58 -> 107658`,
      `1000 -> 100000`, >2dp half-up rounding, and that missing/empty price throws (never silent 0)

### Implementation for User Story 2

- [X] T014 [P] [US2] Implement exact decimal->minor-unit conversion as a PURE function using
      string/integer math (no float) in `scraper/lib/convert.mjs`
- [X] T015 [P] [US2] Implement client-side result validation (non-empty, configurable minimum-record
      floor, per-record shape per `contracts/scraper-output.schema.json`) in `scraper/lib/validate.mjs`
- [X] T016 [P] [US2] Implement the POST helper: warmup `GET {BACKEND_BASE_URL}/api/v1/ping` with
      backoff until 200 (40-60s per-request timeout, above Fly cold start), then a single POST to
      `/api/v1/admin/bond-prices` with the `X-Bond-Ingest-Secret` header (secret from env, never
      logged), in `scraper/lib/post.mjs`
- [X] T017 [US2] Implement `scraper/scrape-bonds.mjs`: launch headless Chromium, open
      `https://next.privat24.ua/bonds`, intercept the `POST /api/p24/pub/bonds` response via a bounded
      `page.waitForResponse`, validate (T015), convert (T014), then post (T016); on ANY failure log the
      reason, exit non-zero, and POST nothing (depends on T014, T015, T016)
- [X] T018 [P] [US2] Create `.github/workflows/refresh-bond-prices.yml`: triggers `schedule: cron
      "0 7 * * 1-5"` + `workflow_dispatch`; `actions/setup-node@v4` (Node 20); `npm ci` in `scraper/`;
      `npx playwright install --with-deps chromium` with `~/.cache/ms-playwright` cached on the pinned
      Playwright version; run the scraper with `BACKEND_BASE_URL=https://api.investguideua.com` and
      `BOND_INGEST_SECRET` from Actions secrets (UTF-8/LF)
- [X] T019 [P] [US2] Create `scripts/refresh-bond-prices.ps1` (PURE ASCII, CRLF): `-BackendBaseUrl`
      param (default `http://localhost:8080`); read the secret from `$env:BOND_INGEST_SECRET` (with an
      optional `-Secret` override) and pass it to the child `node scraper/scrape-bonds.mjs` via the
      environment (not argv); fail on a non-zero scraper exit
- [X] T020 [P] [US2] Write `scraper/README.md`: env vars (`BACKEND_BASE_URL`, `BOND_INGEST_SECRET`),
      the minimum-record floor constant, and how to run locally and in CI
- [X] T021 [US2] Verify `scripts/refresh-bond-prices.ps1` per Constitution V: non-ASCII scan
      `grep -rnP "[^\x00-\x7F]" scripts/refresh-bond-prices.ps1` (expect no matches) AND the PowerShell
      parser (`Parser::ParseFile`); fix any finding (depends on T019)

**Checkpoint**: Scraper converts + validates + (with US3) ingests; CI + local drivers in place.

---

## Phase 5: User Story 3 - Secure machine-to-machine ingest of collected prices (Priority: P2)

**Goal**: A shared-secret endpoint validates and upserts a batch of parsed bonds by ISIN, rejecting
bad secrets and malformed records, and never blanking existing data.

**Independent Test**: POST a batch with a valid secret -> 200 `{accepted,rejected}` and rows stored;
wrong/missing secret -> 401, nothing stored; blank server secret -> all ingest 401; empty array ->
400; non-array / unknown field -> 400; a 1-valid+1-invalid batch -> 200 `{accepted:1, rejected:1}`
with only the valid row stored; re-POST same batch -> no duplicates.

### Tests for User Story 3

- [X] T022 [P] [US3] `BondPriceIngestControllerTest`: valid secret -> 200; wrong/missing secret -> 401;
      blank server secret -> all ingest 401; empty array -> 400; non-array & unknown-field -> 400;
      partial batch -> 200 `{accepted:1,rejected:1}` with only the valid row persisted, in
      `backend/src/test/java/com/investguide/bonds/BondPriceIngestControllerTest.java`
- [X] T023 [P] [US3] `BondPriceServiceTest`: upsert-by-ISIN (no duplicates on re-run); intra-batch
      duplicate ISIN last-wins; per-record programmatic validation drops only the bad record; a single
      `fetchedAt` applied across the batch; empty batch is a guarded no-op, in
      `backend/src/test/java/com/investguide/bonds/BondPriceServiceTest.java`

### Implementation for User Story 3

- [X] T024 [P] [US3] Create `IngestBondRequest` DTO with BOXED types so omission rejects, not silently
      defaults: `@NotBlank isin`, `@NotNull Boolean military`, `@NotBlank @Pattern("UAH|USD|EUR")
      currency`, `@NotBlank String maturity`/`quotationDate` (parsed in service), `@NotNull @Min(0) Long
      sellPriceMinor`/`buyPriceMinor`, `@NotNull Double sellYield`/`buyYield`, in
      `backend/src/main/java/com/investguide/bonds/dto/IngestBondRequest.java`
- [X] T025 [P] [US3] Create `IngestResult` DTO `{ int accepted; int rejected; }` in
      `backend/src/main/java/com/investguide/bonds/dto/IngestResult.java`
- [X] T026 [US3] Implement `BondIngestAuth` helper: constant-time compare of the
      `X-Bond-Ingest-Secret` header against `BondsProperties` secret via `MessageDigest.isEqual` on raw
      bytes (no early length short-circuit); throw `ApiException(UNAUTHORIZED)` when the header is
      missing/wrong OR the configured secret is blank, in
      `backend/src/main/java/com/investguide/bonds/BondIngestAuth.java` (depends on T005)
- [X] T027 [US3] Implement `BondPriceService`: reject empty/non-array batch (400); inject
      `jakarta.validation.Validator` and validate EACH element programmatically (collect violations,
      skip invalid -> `rejected`); parse dates per-record (bad date -> that record rejected); convert
      valid records to `BondPrice`; capture ONE `Instant.now()` for the whole batch as `fetchedAt`;
      upsert by ISIN (last-wins intra-batch; never delete absent ISINs); return `IngestResult`, in
      `backend/src/main/java/com/investguide/bonds/BondPriceService.java` (depends on T007, T008, T024, T025)
- [X] T028 [US3] Implement `BondPriceIngestController` `POST /api/v1/admin/bond-prices`: call
      `BondIngestAuth` FIRST, then delegate to `BondPriceService`; never log the body or the secret
      header, in `backend/src/main/java/com/investguide/bonds/BondPriceIngestController.java`
      (depends on T026, T027)
- [X] T029 [US3] Add `"/api/v1/admin/bond-prices"` to the `PUBLIC_POST` array (POST-only) in
      `backend/src/main/java/com/investguide/common/security/SecurityConfig.java` so the JWT chain does
      not 401 before the secret check (same file as T012 — apply sequentially, do not conflict)

**Checkpoint**: All three stories independently functional; scraper -> ingest -> read works end to end.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Run the mandatory verification gates and the multi-role review (Constitution V & VI).

- [X] T030 Run the backend suite `cd backend; mvn -B test` and confirm all new bonds tests pass
      (depends on T009, T011, T012, T022, T023, T024-T029)
- [X] T031 Run the scraper unit tests `cd scraper; node --test` and confirm the conversion tests pass
      (depends on T013, T014)
- [X] T032 Constitution V final gate: re-run the non-ASCII scan over executed Windows scripts
      `grep -rnP "[^\x00-\x7F]" scripts/refresh-bond-prices.ps1` (expect clean) AND the PowerShell
      parser; confirm `.gitattributes` keeps the `.ps1` CRLF and the `.yml`/`.mjs` LF
- [ ] T033 (NOT RUN in this environment) Run the `quickstart.md` flow end to end against local Docker
      Compose (set secret in `.env`, run `scripts/refresh-bond-prices.ps1`, then read
      `GET /api/v1/bond-prices` as ADMIN) and confirm fresh rows with `fetchedAt`. Requires Docker
      Desktop running and live network access to next.privat24.ua (real headless scrape) - neither was
      available here, so this remains a manual verification step for the developer.
- [X] T034 Constitution VI: run at least two role sub-agents (Back-end lead + DevOps, QA on the
      validation/security surface) over the diff, INCLUDING the scan/parse/compile gate; apply or
      report findings before marking the feature done

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (P1)**: no dependencies.
- **Foundational (P2)**: depends on Setup; BLOCKS all user stories.
- **US1 (Phase 3)**: depends on Foundational (needs `BondPrice` + repository).
- **US2 (Phase 4)**: depends on Foundational. Conversion/validation/interception/CI/script slices are
  independently testable. **Cross-story note**: the scraper's live POST target is built in US3, so the
  full end-to-end POST + read demo requires US3 complete (both exist together locally and in prod).
- **US3 (Phase 5)**: depends on Foundational (needs `BondPrice` + repository + `BondsProperties`).
- **Polish (Phase 6)**: depends on all targeted stories.

### Cross-file coordination

- `SecurityConfig.java` is edited by **T012** (US1 GET rule) and **T029** (US3 POST allowlist) — same
  file, apply sequentially, not in parallel.
- `BondPrice` (T007) is shared by US1 and US3; `BondsProperties` (T005) is shared by foundation/US3.

### Within each story

- Tests are written first and expected to fail until implementation lands.
- Entity/DTO -> service -> controller -> security rule.

### Parallel opportunities

- Setup: T001, T002, T003 all [P].
- Foundational: T004, T005, T007 [P] (T006 after T005; T008 after T007).
- US1: T009 and T010 [P]; then T011 -> T012.
- US2: T013, T014, T015, T016, T018, T019, T020 [P]; T017 after T014/T015/T016; T021 after T019.
- US3: T022, T023, T024, T025 [P]; then T026/T027 -> T028 -> T029.

---

## Parallel Example: User Story 3

```text
# After Foundational, launch the US3 tests and DTOs together:
Task: "BondPriceIngestControllerTest in backend/src/test/java/com/investguide/bonds/BondPriceIngestControllerTest.java"
Task: "BondPriceServiceTest in backend/src/test/java/com/investguide/bonds/BondPriceServiceTest.java"
Task: "IngestBondRequest DTO in backend/src/main/java/com/investguide/bonds/dto/IngestBondRequest.java"
Task: "IngestResult DTO in backend/src/main/java/com/investguide/bonds/dto/IngestResult.java"
```

---

## Implementation Strategy

### MVP first (smallest demonstrable slice)

1. Phase 1 Setup -> Phase 2 Foundational.
2. Phase 3 **US1** (read). Seed one `bondPrices` row, read it as ADMIN. **STOP & VALIDATE.** This is
   the minimal demonstrable increment (fresh data not yet automated).

### Full P1 value (automated fresh prices)

3. Phase 5 **US3** (ingest endpoint) — small; unlocks the scraper's POST target.
4. Phase 4 **US2** (scraper + CI + local script). Run locally end to end -> read shows fresh rows.
   > Practical note: although US2 is P1 and US3 is P2 by spec priority, implement US3 just before (or
   > alongside) US2 since the scraper posts to the ingest endpoint. The phases keep spec priority
   > order; this is the recommended *build* order.

### Then

5. Phase 6 polish: run `mvn -B test`, `node --test`, the ASCII/parser gate, the quickstart flow, and
   the mandatory two-role review.

---

## Notes

- Money is integer minor units everywhere on the Java side (`long`); the only decimal math is in the
  scraper's pure `convert.mjs` (string math, unit-tested). No float in any Java-facing contract.
- The ingest secret is fail-closed-on-blank (rejects ingest, does not crash startup) and never logged.
- Preserve-on-failure: the scraper POSTs nothing on a failed/empty scrape; the backend rejects empty
  batches and never deletes absent ISINs — so a bad run can never blank the collection (SC-006).
- `[P]` = different files, no incomplete dependency. Commit after each task or logical group.
