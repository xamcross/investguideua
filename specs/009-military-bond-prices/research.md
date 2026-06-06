# Phase 0 Research: Military Bond Price Refresh

All major decisions were confirmed in the feature request; this document records them in
decision/rationale/alternatives form and resolves the few remaining implementation unknowns. No
open `NEEDS CLARIFICATION` items remain.

## R1. Where the headless-browser scraper runs

- **Decision**: A scheduled GitHub Actions workflow on `ubuntu-latest` runs Playwright + Chromium.
  Not on the Fly backend, not as an in-process Spring `@Scheduled` job.
- **Rationale**: The Fly machine is 512 MB with the JVM at `MaxRAMPercentage=70`; launching Chromium
  there would OOM. The backend has `min_machines_running=0` (scales to zero), so an in-process
  scheduler would not reliably be awake at 10:00 Kyiv. GitHub's hosted runners are free for this
  cadence, already used for CI, and isolate the heavy browser workload from production (Constitution I
  — no new always-on infra; spec FR-016).
- **Alternatives considered**: (a) In-process `@Scheduled` + headless browser on Fly — rejected
  (OOM + scale-to-zero). (b) A separate always-on worker/Fly machine — rejected (added infra/cost,
  violates MVP discipline). (c) Reconstruct the `xref` handshake in Java with `WebClient` — rejected
  as an explicit non-goal: the handshake is a cross-domain anti-bot flow that is fragile to replicate
  and would break silently when PrivatBank changes it.

## R2. How quotes are obtained from PrivatBank

- **Decision**: Load `https://next.privat24.ua/bonds` in Chromium, let PrivatBank's own SPA JavaScript
  complete the `xref` anti-bot handshake, and intercept the `POST /api/p24/pub/bonds` XHR **response**
  JSON directly (Playwright response/route interception). Do not craft the API call ourselves.
- **Rationale**: The `xref` is an anonymous session token minted by the SPA; reusing the page's own
  completed handshake is far more robust than reproducing it. Reading the response the page already
  fetched avoids any need to mint or forward the token (spec FR-003).
- **Alternatives considered**: Replaying the captured `xref` in a raw HTTP POST — rejected: the token
  is short-lived and origin-bound; the browser-intercept approach already has the parsed JSON in hand.

## R3. Cadence / scheduling

- **Decision**: Cron `0 7 * * 1-5` (UTC), Monday-Friday, plus `workflow_dispatch` for manual runs.
- **Rationale**: 07:00 UTC is 10:00 Kyiv in summer (EEST, UTC+3) and 09:00 in winter (EET, UTC+2).
  The one-hour DST drift is explicitly accepted (spec Assumptions). GitHub cron does not support
  time zones, so a fixed UTC schedule with accepted drift is the simplest correct choice. Business
  days only (`1-5`) matches "once per business day". `workflow_dispatch` satisfies FR-002 (manual).
- **Alternatives considered**: Two seasonal crons toggled for DST — rejected as needless complexity
  for an accepted one-hour drift. Daily incl. weekends — rejected (no new quotes on non-trading days).

## R4. Money conversion to integer minor units

- **Decision**: Convert decimal prices to integer minor units (kopiykas/cents) in the scraper using
  string/integer arithmetic, never floating point. `1076.58 -> 107658`. The conversion is a pure
  function with unit tests (`scraper/lib/convert.mjs`, `node --test`). The backend stores and
  validates `long` minor-unit fields and never re-derives them from a float.
- **Rationale**: Constitution IV mandates integer minor units and prohibits float money math. Doing
  the conversion as string math (split on the decimal point, pad/truncate to 2 fraction digits,
  combine as integer) avoids IEEE-754 drift (e.g. `1076.58 * 100` is not exactly `107658.0`). Prices
  are quoted per 1000 face value; the per-1000 quote is stored as-is in minor units (no rescaling) so
  the stored number is the directly comparable quote.
- **Edge cases**: more than 2 fraction digits -> round half-up deterministically (the scraper logs
  when source precision exceeds 2 dp; the backend cannot detect this since it receives already-integer
  minor units); missing/empty price -> reject the record (validation). Currency is carried per record
  so USD/EUR cents and UAH kopiykas are all "minor units of the row's currency".
- **Alternatives considered**: `double * 100 + Math.round` — rejected (float drift). Doing conversion
  on the backend from a transmitted decimal string — viable, but keeping it in the scraper keeps the
  ingest payload already-integer and the backend contract float-free end to end.

## R5. Machine-to-machine ingest authentication

- **Decision**: `POST /api/v1/admin/bond-prices` is authenticated by a shared secret in a custom
  header (`X-Bond-Ingest-Secret`), compared in constant time (`MessageDigest.isEqual` on raw bytes,
  no early return on length mismatch), NOT by a user JWT. The route MUST be added explicitly to the
  `SecurityConfig` `PUBLIC_POST` allowlist (the chain is an explicit allowlist; otherwise
  `.anyRequest().authenticated()` 401s a no-Bearer request before the secret check). The secret check
  is a helper invoked from the controller (so `GlobalExceptionHandler` formats the standard
  `ErrorResponse`), throwing `ApiException(UNAUTHORIZED)` on failure. The secret is configured as a
  **blank-tolerant** `String` in `BondsProperties` (NOT `@NotBlank`) so an unset secret fails *ingest*
  closed (`401`) rather than failing app startup; `BondsProperties` must be registered in
  `@EnableConfigurationProperties` (no `@ConfigurationPropertiesScan` exists). DB credentials never
  leave the backend.
- **Why fail-closed (not the MONO_TOKEN analogy):** a blank `MONO_TOKEN` only 502s an unused feature;
  a blank ingest secret on a `permitAll` route would be an open unauthenticated write endpoint if it
  ever defaulted to allow. So blank-secret-rejects-all is a hard security invariant with its own test,
  closer to the required-secret/fail-fast posture than the optional-token pattern.
- **Rationale**: The scraper is a non-interactive machine with no user identity; minting a long-lived
  admin JWT for it would be a larger blast radius than a single-purpose ingest secret. Constant-time
  comparison avoids timing oracles. Fail-closed on a blank secret matches the constitution's
  "fail fast / no silent insecure default" posture while still letting the rest of the app run
  locally (mirrors the existing optional `MONO_TOKEN` pattern, but rejecting rather than 502-ing).
  Spec FR-008/FR-009/FR-011.
- **Secret provisioning**: `BOND_INGEST_SECRET` env var on the backend (Fly secret) and as a GitHub
  Actions secret for the scraper; for local dev it is set in `.env`/passed to the PowerShell helper.
  Rotation tooling is out of scope (spec Assumptions).
- **Alternatives considered**: (a) Reuse the user JWT with an ADMIN service account — rejected (user
  auth for a machine; credential lifetime/rotation burden). (b) mTLS — rejected (overkill for MVP,
  Fly/GH plumbing cost). (c) IP allowlist — rejected (GitHub runner IPs are broad and rotate).

## R6. Persistence model and idempotency

- **Decision**: New MongoDB collection `bondPrices`, document keyed by `isin` (`@Id`). Uniqueness is
  implicit in the `_id` key — no `@Indexed(unique = true)` is needed on the id (the `_id` index always
  exists; `auto-index-creation` only matters for secondary indexes). Ingest upserts by ISIN
  (update-in-place when present, insert otherwise) so repeated runs never create duplicates. Within a
  single batch, the last occurrence of a duplicate ISIN wins deterministically (process in order).
- **Rationale**: Latest-quote-only retention (spec Assumptions) means one row per instrument; ISIN is
  the natural stable key (mirrors the providers collection using a stable string `_id`). Spec FR-007,
  SC-002.
- **Alternatives considered**: Append-only history with a separate "latest" view — rejected (history
  is explicitly out of scope for v1). Auto-generated `_id` + secondary ISIN index — rejected (ISIN as
  `_id` makes the upsert key the document identity and guarantees uniqueness for free).

## R7. Preserve-on-failure / empty-batch handling

- **Decision**: A failed scrape (source down, handshake fails, zero/clearly-truncated data) results
  in NO write that blanks existing data. The scraper treats an empty/parse-failed result as a job
  failure and does not POST. The backend additionally rejects an empty ingest batch (no-op, reported
  as such) so a stray empty POST can never wipe the collection. Upsert-only (never delete) means a
  partial batch updates only the ISINs present and leaves others intact.
- **Rationale**: Spec edge cases + FR-015 + SC-006: a bad run must never reduce the stored set to
  zero. Upsert-without-delete is inherently non-destructive to absent rows.
- **Alternatives considered**: Replace-all (delete then insert) semantics — rejected (a truncated
  batch would silently drop instruments; a failed insert mid-way could empty the collection).

## R8. Read endpoint authorization

- **Decision**: `GET /api/v1/bond-prices` requires the ADMIN role, configured in `SecurityConfig`
  exactly like `GET /api/v1/providers` (`.requestMatchers(GET, "/api/v1/bond-prices").hasRole("ADMIN")`).
  Anonymous -> 401, authenticated non-admin -> 403. Serves stored data only; never triggers a scrape.
- **Rationale**: Spec FR-012/FR-013/FR-014 require the same gating as providers and read-from-cache
  semantics. Reusing the established pattern keeps the security model consistent and testable.
- **Alternatives considered**: A new permission/role — rejected (Constitution: no new role model;
  spec says reuse providers gating).

## R9. Local + production parity

- **Decision**: One scraper, two drivers. Local: `scripts/refresh-bond-prices.ps1` (pure ASCII) runs
  `node scraper/scrape-bonds.mjs` with `BACKEND_BASE_URL=http://localhost:8080` against the Docker
  Compose stack. Production: `.github/workflows/refresh-bond-prices.yml` runs the same script with
  `BACKEND_BASE_URL=https://api.investguideua.com`.
- **Cold-start wake (explicit):** before POSTing, the scraper first **warms up** the scaled-to-zero
  Fly backend by polling the public `GET /api/v1/ping` (already `permitAll`) with backoff until `200`,
  using a per-request timeout (40-60s) above the worst-case JVM cold start (~20-35s on shared-cpu-1x,
  per `backend/fly.toml`). Only once awake does it POST the batch **once**. A bare retried POST against
  a still-booting app is a poor wake probe and risks timing out inside the boot window; the GET warmup
  avoids that. Secret is passed to the child Node process via environment, never on argv.
- **Rationale**: Spec FR-017 + the local-Compose requirement. Sharing one scraper avoids drift
  between what is tested locally and what runs in prod. The PowerShell wrapper is the only
  Windows-executed artifact, so it is the one held to the ASCII rule.
- **Alternatives considered**: Separate local vs CI scrapers — rejected (drift risk). A Docker
  Compose service that runs Chromium — rejected (heavy local image; the dev only needs an occasional
  manual refresh, and Playwright installs Chromium on demand).

## R10. Verification strategy (Constitution V/VI)

- **Decision**: Backend changes verified by `mvn -B test` (new controller/service tests). Scraper
  conversion verified by `node --test`. The PowerShell helper verified by a non-ASCII scan
  (`grep -rnP "[^\x00-\x7F]"`) and the PowerShell parser. The GitHub workflow YAML and scraper JS are
  Linux-consumed (UTF-8/LF). Two role sub-agents (Back-end lead, DevOps; QA on the security/validation
  surface) review, including the scan/parse step, before the task is marked done.
- **Rationale**: Mandatory gates per Constitution V and VI; the browser-interception path cannot be
  meaningfully unit-tested in CI, so it is validated by the scheduled/manual run and documented as
  such rather than implied to be covered. The secret is passed to the PowerShell helper via
  `$env:BOND_INGEST_SECRET` (defaulted from `.env`) rather than a `-Secret` argv parameter, so it does
  not land in `Get-History` or a child process command line; `-Secret` is accepted only as an override.

## R11. Defining scrape success (fail-loud, do-not-POST)

- **Decision**: The scraper treats a run as **failed** (non-zero exit, no POST) unless ALL hold:
  (a) the `POST /api/p24/pub/bonds` XHR response is intercepted within a bounded
  `page.waitForResponse` timeout and is 2xx; (b) the parsed result is a non-empty array whose length
  meets a configured **minimum-record floor** (rejects "clearly truncated" feeds); (c) every record
  passes client-side schema validation (`scraper-output.schema.json`) after conversion. Any failure
  -> log the reason, exit non-zero, POST nothing (existing stored prices preserved).
- **Rationale**: Spec edge cases + FR-015 + SC-006 require a bad run to never blank stored data, and
  "clearly truncated" must be operationally defined or it is unenforceable. The minimum-record floor
  is a config constant documented in `scraper/README.md`; a legitimately short feed below the floor is
  a tradeoff accepted in favor of never ingesting a partial scrape.
- **Alternatives considered**: Trusting whatever the page returns -> rejected (silent partial data).
  No floor, only non-empty -> rejected (a 1-record truncation would pass).

## R12. CI workflow specifics (Playwright + scheduling reliability)

- **Decision**: The workflow uses `actions/setup-node@v4` (Node 20, matching `ci.yml`), `npm ci` in
  `scraper/`, then `npx playwright install --with-deps chromium`, with `~/.cache/ms-playwright` cached
  keyed on the pinned Playwright version (kept in `scraper/package.json`). Headless Chromium with
  bounded navigation/XHR timeouts. Runs to completion well under the <5 min target.
- **Scheduled-run reliability / staleness detection**: GitHub disables `schedule:` triggers after
  ~60 days of repo inactivity, and cron firing is best-effort (can be delayed 5-30+ min). Mitigations:
  (a) `workflow_dispatch` always available for a manual kick; (b) GitHub's default email-on-failed-run
  notifies the repo owner when a scheduled run *runs and fails* (it does NOT fire if the schedule is
  disabled); (c) the read DTO exposes per-record `fetchedAt` so an admin can spot staleness directly.
  Full alerting/uptime monitoring is out of scope for the MVP (Constitution I) but the staleness
  signal (`fetchedAt`) and the manual trigger are the documented operator safety net.
- **Rationale**: Without the install/`--with-deps` step the browser is missing OS libs and the job
  fails; without caching, runs are slow. The auto-disable and delay caveats are real and must be
  documented so a late or stopped refresh is not mistaken for a code fault.

## R13. Partial-batch staleness (known, accepted behavior)

- **Decision**: Upsert-without-delete means a successful run returning a subset (delisted instrument,
  or a partial-but-above-floor scrape) leaves prior rows for absent ISINs in place. Those rows are
  distinguishable only by their older `fetchedAt`. This is accepted v1 behavior; the read surface must
  expose per-row `fetchedAt` so stale rows are visible.
- **Rationale**: Non-destructiveness (SC-006) is worth more than auto-pruning absent instruments in
  v1; pruning risks blanking on a legitimate-looking partial. Documented as a known limitation rather
  than left implicit.
