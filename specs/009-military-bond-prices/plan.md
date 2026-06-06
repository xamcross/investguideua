# Implementation Plan: Military Bond Price Refresh

**Branch**: `009-military-bond-prices` | **Date**: 2026-06-06 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/009-military-bond-prices/spec.md`

## Summary

Surface up-to-date Ukrainian military bond (military OVDP) prices inside InvestGuideUA, refreshed once
per business day. A scheduled GitHub Actions workflow runs a headless-browser scraper (Playwright +
Chromium on Ubuntu) that loads `next.privat24.ua/bonds`, lets PrivatBank's own JavaScript complete its
anti-bot handshake, and intercepts the `/api/p24/pub/bonds` XHR JSON. The scraper converts prices to
integer minor units (kopiykas), keeps yields as doubles, and POSTs the batch to a new shared-secret
machine-to-machine endpoint `POST /api/v1/admin/bond-prices`. The backend validates and upserts (by
ISIN) into a new MongoDB `bondPrices` collection. Stored prices are read via `GET /api/v1/bond-prices`,
gated to the ADMIN role exactly like the existing `/api/v1/providers` endpoint. The scraper runs both
locally (against `http://localhost:8080` via a pure-ASCII PowerShell helper) and in production (against
`https://api.investguideua.com`, waking the scaled-to-zero Fly backend).

## Technical Context

**Language/Version**: Backend Java 21 (Spring Boot 3.4); Scraper Node.js 20 (Playwright). No new
backend language; the scraper is a new, self-contained Node tool outside the deployable JAR.  
**Primary Dependencies**: Backend — Spring Web, Spring Data MongoDB, Jakarta Bean Validation (all
already present). Scraper — `playwright` (Chromium) + native `fetch` (Node 20). No new backend
dependency is required.  
**Storage**: MongoDB 7.x — one new collection `bondPrices`, keyed/uniquely indexed by `isin`.  
**Testing**: Backend — JUnit/Spring Boot Test + the existing acceptance suite (`mvn -B test`).
Scraper — a pure-function unit test for the price/parse conversion (Node `--test`); the browser
interception path is validated by manual/scheduled runs (documented, not mocked in CI).  
**Target Platform**: Backend — single Linux container on Fly.io (512 MB, `min_machines_running=0`).
Scraper — GitHub-hosted `ubuntu-latest` runner (scheduled) and the developer's Windows + Docker
Compose machine (local).  
**Project Type**: Web service (Java backend + Angular frontend) plus a new headless-scraper CI tool.  
**Performance Goals**: One scheduled run per business day; a run completes well within a GitHub Actions
job (target < 5 min including Chromium launch and Fly cold-start wake). No throughput target — the
read endpoint serves a small (tens of rows) catalog from MongoDB.  
**Constraints**: Money is integer minor units only (no float for prices). The scraper MUST NOT run on
the Fly backend (Chromium would OOM the 512 MB machine) nor as an in-process `@Scheduled` job (backend
scales to zero). Any Windows-executed `.ps1` MUST be pure ASCII (Constitution V). DB credentials stay
on the backend only.  
**Scale/Scope**: Tens of bond instruments; a handful of admins. Latest-quote-only (no history in v1).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0).

- [x] **I. MVP Discipline**: No new service, queue, or scaling infra is added to the deployment
      topology. The single backend + single MongoDB + managed LLM stay intact. The scraper is not a
      new *deployed* runtime — it is a CI job on GitHub's free runners plus a local dev script, with
      zero added always-on infrastructure. The backend gains only two lightweight endpoints and one
      collection. Scope is bounded to the spec (latest-quote-only, no history, no coupons).
- [x] **II. Fixed Stack**: Angular 17+ / Java 21 / Spring Boot 3.4 / MongoDB 7 unchanged. No new
      backend dependency (uses Spring Web + Spring Data Mongo + Bean Validation already on the
      classpath). The scraper's Playwright/Chromium is a CI-only dev tool on a current release line,
      not a backend or frontend runtime dependency, so the fixed product stack is untouched. No
      deprecated/EOL libraries introduced.
- [x] **III. LLM Guardrails**: N/A — this feature performs no LLM calls. It neither weakens nor
      touches the existing server-side, catalog-grounded LLM guardrails.
- [x] **IV. Financial Integrity**: Bond prices are stored as integer minor units (kopiykas);
      conversion happens in the scraper using string/integer arithmetic (no float) and the backend
      stores/validates `long` minor-unit fields. Yields are reference percentages (doubles), not money.
      No token-ledger or balance mutation is involved, so the negative-balance and idempotency rules
      do not apply; the disclaimer rules concern search results and are unaffected.
- [x] **V. Encoding & Verification**: The one Windows-executed script (`scripts/refresh-bond-prices.ps1`)
      will be authored pure ASCII and verified by a non-ASCII scan plus the PowerShell parser.
      Linux-consumed files (the GitHub workflow YAML, scraper JS, Dockerfile-adjacent configs) stay
      UTF-8/LF. "Done" is gated on `mvn -B test` (Java) and a `node --test` run (scraper conversion),
      or an explicit static-only declaration if a runtime is unavailable.
- [x] **VI. Multi-Role Review**: At least two role sub-agents (Back-end lead + DevOps, with QA on the
      validation/security surface) will review before this work is marked done, including the actual
      scan/parse/compile per Principle V.

**Result**: PASS — no violations; Complexity Tracking table not required.

## Project Structure

### Documentation (this feature)

```text
specs/009-military-bond-prices/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── ingest-bond-prices.post.md      # POST /api/v1/admin/bond-prices (shared secret)
│   ├── read-bond-prices.get.md         # GET  /api/v1/bond-prices (ADMIN)
│   └── scraper-output.schema.json      # parsed-bond JSON shape the scraper emits
└── checklists/
    └── requirements.md  # spec quality checklist (from /speckit.specify)
```

### Source Code (repository root)

```text
backend/src/main/java/com/investguide/bonds/          # NEW package
├── BondPrice.java                 # @Document("bondPrices"); isin @Id/unique; long minor-unit prices
├── BondPriceRepository.java       # MongoRepository<BondPrice, String>
├── BondPriceService.java          # validate + upsert-by-ISIN; preserve-on-empty guard
├── BondPriceController.java       # GET /api/v1/bond-prices (ADMIN)
├── BondPriceIngestController.java # POST /api/v1/admin/bond-prices (shared secret)
├── BondIngestAuth.java            # constant-time shared-secret check (X-Bond-Ingest-Secret)
├── dto/
│   ├── BondPriceResponse.java     # read DTO
│   ├── IngestBondRequest.java     # one bond in a batch (validated)
│   └── IngestResult.java          # {accepted, rejected}
└── package-info.java

backend/src/main/java/com/investguide/config/
└── BondsProperties.java           # @ConfigurationProperties("bonds"): blank-tolerant ingest secret
                                    #   (NOT @NotBlank -> unset fails INGEST closed, not startup)

backend/src/main/java/com/investguide/InvestGuideApplication.java           # + BondsProperties in
                                    #   @EnableConfigurationProperties (explicit allowlist, no scan)
backend/src/main/resources/application.yml             # + bonds.ingest.secret (env BOND_INGEST_SECRET)
backend/src/main/java/com/investguide/common/security/SecurityConfig.java   # + POST /api/v1/admin/
                                    #   bond-prices in PUBLIC_POST; GET /api/v1/bond-prices hasRole ADMIN

backend/src/test/java/com/investguide/bonds/           # NEW tests
├── BondPriceIngestControllerTest.java   # secret accept/reject; blank-server-secret -> all 401;
│                                        #   unknown-field/non-array -> 400; empty-batch -> 400;
│                                        #   partial batch (1 valid + 1 invalid) -> 200 {accepted:1,
│                                        #   rejected:1} and only the valid row stored
├── BondPriceControllerTest.java         # ADMIN 200 / non-admin 403 / anon 401 / empty list -> []
└── BondPriceServiceTest.java            # upsert-by-ISIN dedup; intra-batch dup last-wins;
                                         #   per-record programmatic validation; single fetchedAt/batch

scraper/                                                # NEW top-level Node tool (CI + local)
├── package.json                   # "type":"module", PINNED playwright version, "test" script
├── scrape-bonds.mjs               # Chromium -> intercept XHR (bounded waitForResponse) -> validate
│                                  #   (min-record floor + schema) -> warmup GET -> POST; fail-loud
├── lib/
│   ├── convert.mjs                # exact decimal->minor-unit (string math), pure & unit-tested
│   ├── post.mjs                   # warmup GET /api/v1/ping (backoff) then single POST; 40-60s timeout
│   └── validate.mjs               # client-side schema/floor check before POST (R11)
├── test/
│   └── convert.test.mjs           # node --test: 1076.58 -> 107658, rounding/edge cases
└── README.md                      # env vars, min-record floor, how to run locally and in CI

.github/workflows/
└── refresh-bond-prices.yml        # NEW: cron "0 7 * * 1-5" + workflow_dispatch; setup-node@v4 (Node
                                   #   20), npm ci, npx playwright install --with-deps chromium (cached)

scripts/
└── refresh-bond-prices.ps1        # NEW (pure ASCII): run scraper vs http://localhost:8080; secret
                                   #   from $env:BOND_INGEST_SECRET (not argv); ASCII-scan + parser gate

.env.example                       # + commented BOND_INGEST_SECRET entry (env_file passes it to Compose)
```

**Structure Decision**: The backend follows the existing per-domain package convention (mirrors
`com.investguide.catalog`: entity + repository + service + controller(s) + `dto/` + `package-info`).
Config uses the established `@ConfigurationProperties` record pattern (`BondsProperties` alongside
`SecurityProperties`/`AppProperties`). The read endpoint reuses the exact ADMIN gating already applied
to `/api/v1/providers` in `SecurityConfig`. The scraper is a new self-contained top-level `scraper/`
directory (Node 20 + Playwright) so it never enters the deployable JAR or the Fly machine, satisfying
the "not on the backend" constraint. The GitHub workflow and a pure-ASCII PowerShell helper drive the
same scraper in CI and locally.

> NOTE: The tree diagrams above are display-only. Do NOT paste them into a Windows-executed script.

## Phase 0 / Phase 1 outputs

- Phase 0 research: [research.md](./research.md)
- Phase 1 data model: [data-model.md](./data-model.md)
- Phase 1 contracts: [contracts/](./contracts/)
- Phase 1 quickstart: [quickstart.md](./quickstart.md)

## Review findings incorporated (multi-role review, Constitution VI)

A Back-end Lead and a DevOps/Security reviewer reviewed this plan (both: APPROVE WITH CHANGES). The
following blocking/should-fix items were folded into the artifacts above and into research.md /
data-model.md / contracts:

- **Per-record validation is programmatic, not `@Valid` on a `List`** — Spring does not cascade
  `@Valid` to list elements and a Bean Validation failure would 400 the whole batch, contradicting
  FR-010's per-record drop. `BondPriceService` injects `jakarta.validation.Validator` and loops.
- **`BondsProperties` must be added to `@EnableConfigurationProperties`** in `InvestGuideApplication`
  (this project uses an explicit allowlist; there is no `@ConfigurationPropertiesScan`).
- **Ingest route must be explicitly `permitAll`** (added to `PUBLIC_POST`); the secret check is a
  controller-invoked helper throwing `ApiException(UNAUTHORIZED)` so the standard `ErrorResponse` is
  returned. The secret field is **blank-tolerant** (not `@NotBlank`) -> unset fails *ingest* closed,
  not startup. Constant-time compare via `MessageDigest.isEqual`.
- **DTO fields are boxed** (`Boolean`/`Long`/`Double` + `@NotNull`) so an omitted JSON field is a
  rejection, never a silent `false`/`0` (financial-integrity adjacency). Currency enforced by
  `@Pattern`; dates parsed in the per-record loop.
- **Cold-start handling is an explicit warmup GET `/api/v1/ping` (backoff) then a single POST** with
  a 40-60s per-request timeout above the ~20-35s Fly JVM cold start — not a bare retried POST.
- **Scrape success is operationally defined** (bounded `waitForResponse`, 2xx, non-empty + min-record
  floor, per-record schema validation); any failure exits non-zero and POSTs nothing.
- **CI specifics**: `setup-node@v4` Node 20, `npm ci`, `npx playwright install --with-deps chromium`,
  cached on the pinned Playwright version. Scheduled-run auto-disable (~60 days) + best-effort timing
  documented; `fetchedAt` + `workflow_dispatch` are the MVP staleness/operator safety net.
- **Secret hygiene**: passed via environment (not argv), never logged; `.env.example` gains a
  commented entry; `docker-compose.yml` `env_file: .env` already propagates it to the Compose backend.
- **Doc corrections**: ISIN uniqueness is implicit in `_id` (no `@Indexed(unique=true)` on the id);
  single `Instant.now()` per batch for `fetchedAt`; partial-batch staleness documented as accepted
  v1 behavior (R13).

Deferred (out of MVP scope, explicitly): full alerting/uptime monitoring of the scheduled run, and
auto-pruning of instruments absent from a successful batch.

## Complexity Tracking

No constitution violations. Table intentionally omitted.
