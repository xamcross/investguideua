# Phase 0 Research: Precious Metals Price Refresh

**Feature**: 011-precious-metals-prices | **Date**: 2026-06-08

This document resolves the open decisions needed before design. The architecture deliberately mirrors
feature 009 (military bond prices); only the deltas and genuinely new decisions are recorded here.

## R1. Metal disambiguation for an investment option (the key decision)

**Problem**: FR-018 requires that a presented PRECIOUS_METALS option carry the exact current price for
"the corresponding metal". But the catalog's PRECIOUS_METALS providers (e.g. `privatbank-metals`,
`mtbbank-metals`) sell **both** gold and silver - the `Provider` record has no gold/silver
discriminator (only Ukrainian free-text in `description`). So the metal cannot be derived from the
provider.

**Decision**: The metal is a model-emitted, server-validated discriminator. The advisor prompt
(PromptBuilder, BE-S4) instructs the model, for a precious-metals recommendation, to include a field
`"metal": "GOLD" | "SILVER"`. The server (AdvisorOutputParser) validates it against the two-value
enum; the **price** is then backfilled server-side from stored data and the model can never supply it.

**Rationale**:
- Choosing *which* instrument to recommend (gold vs silver) is already the model's editorial job, just
  like the `instrument`, `liquidity`, and `rationale` text it produces. Grounding the *price* (the
  verifiable fact) server-side is exactly the existing anti-hallucination pattern (§8.3): the model
  proposes, the server grounds identity/reference fields from trusted data.
- A dedicated enum field is server-validatable and unit-testable; an invalid/missing value
  deterministically drops the option (FR-019), consistent with the existing out-of-catalog drop.
- Keeps the price 100% grounded (SC-009): the model contributes the metal choice only, never a number.

**Alternatives considered**:
- *Parse the metal from the model's `instrument` free text* (keyword match "gold"/"золото",
  "silver"/"срібло"): rejected - fragile, locale-dependent, and derives a key decision from
  unvalidated text. The discriminator field is the same information in a validatable form.
- *Split the catalog into single-metal providers* (a gold provider, a silver provider): rejected -
  invasive change to the seeded catalog, and untrue to reality (these banks sell both metals).
- *Show both gold and silver prices on every metals option*: rejected - contradicts the clarified
  single canonical value (Clarifications Q3/Q4) and the drop-on-missing rule.

**Guardrail impact (Principle III)**: The prompt gains one optional field with a strict server-side
enum check and drop-on-invalid; output stays strict-JSON validated. This strengthens, not weakens,
grounding. The prompt change must update the **literal JSON schema block and the enum-preservation
rule** in PromptBuilder (not just prose), and `PromptBuilderTest` must assert the `metal` instruction
is present - otherwise the model never emits `metal` and every metals option silently drops.

**Two-stage enforcement (resolves the FR-019/SC-010 failure-path hazard)**: Catalog grounding stays the
hallucination gate that may throw (all-off-catalog => `AdvisorOutputException` => retry/fail). Metal
grounding is a **separate, non-throwing post-filter**: a PRECIOUS_METALS option references a real
in-catalog provider, so dropping it for a missing metal/price is NOT a hallucination and MUST NOT
throw, even if it empties the final list (that yields a legitimate empty result, never a 502 + charged
retry). See contracts/investment-option-metal-grounding.md.

**Source payload shape (from the feature request sample)**: The endpoint returns
`{ "status": <boolean>, "metalRates": { "gold": { "rates": { "one"|"two"|"three": { "<weightKey>":
{ "size": "<weightKey>", "prices": { "purchaseRate": "<str>", "saleRate": "<str>" } } } },
"date": "DD.MM.YYYY" }, "silver": { ... } } }`. The collector treats a non-`true` `status` (note:
boolean `true`, NOT the bond feed's string `"success"`) as a failure (spec Edge Cases). `weightKey`
is taken verbatim from the object key (equal to the inner `size`); `weightGrams` is `Number(weightKey)`.

## R2. Canonical price value injected into an option

**Decision**: The single value attached to a metals option is the **sale rate** (the user's
acquisition price), in **integer minor units (kopiykas) per gram**, taken from the **primary retail
rate group** (`one`, config-overridable) at the **smallest available weight tier** for that metal
(gold's 1 g tier; silver's smallest offered, 10 g). Surfaced on the option as
`metalPricePerGramMinor` plus the resolved `metal`.

**Rationale**: An investment option represents acquiring the asset, so the bank's sale rate is the
meaningful figure (Clarifications Q3). The source's rates are already per-gram prices that decrease
with bar size (volume discount); the smallest tier is the headline single-unit retail price. The
primary group `one` is PrivatBank's standard retail bullion group (finest weight granularity, highest
rates). Making the group key config-overridable (`metals.primaryRateGroup`) avoids a hardcode if the
source relabels groups.

**Alternatives**: purchase rate (sell-back) or carrying both rates - rejected per Q3. A fixed
reference weight other than "smallest" - rejected; smallest tier is the recognizable retail unit and
is unambiguous to select (min `weightGrams`).

## R3. Storage shape, keying, and dedup

**Decision**: New MongoDB collection `metalPrices`, one document per (metal, rateGroup, weight). The
deterministic composite key is the document `@Id` string `"<METAL>:<rateGroup>:<weightKey>"`
(e.g. `GOLD:one:1`, `SILVER:one:10`). `save()`/`saveAll()` upsert by `_id`, giving FR-007/SC-002 for
free exactly as ISIN does for bonds - no `@Indexed(unique=true)`, no delete path.

**Rationale**: Directly reuses the 009 keying mechanism (natural-key `_id` upsert). The composite key
is built from the verbatim `weightKey` token (FR-004a) so `"2.5"` never fragments. A separate numeric
`weightGrams` (double, a dimension label - not money) is stored for ordering ("smallest tier"
selection in R2); it does not participate in the key.

**Alternatives**: generated `_id` + unique compound index + upsert-by-query - rejected; more code and
easy to reintroduce duplicates, with no benefit over a deterministic `_id`.

**Key-fragmentation caveat (the one way metals diverges from ISIN)**: the composite `_id` is collision-
proof only if each token is canonical. The scraper takes `metal`/`rateGroup`/`weightKey` verbatim from
the source object keys, so as long as the source uses one stable spelling per weight (it does: the key
equals the inner `size`), `"1"` and `"1.0"` cannot both appear for the same gram size. The scraper MUST
NOT reformat `weightKey`, and none of the three tokens may contain the `:` delimiter (source keys are
metal names, `one|two|three`, and numeric strings - none contain `:`), so the key is unambiguous.

## R4. Price string parsing (whitespace thousands separator)

**Decision**: Extend the scraper's existing `toMinorUnits` (scraper/lib/convert.mjs) to strip **all
Unicode whitespace** from string inputs (via a whitespace class - JS `\s` plus non-breaking/figure/
thin/narrow spaces U+00A0, U+2007, U+2009, U+202F - not a hand-listed set, per FR-005 "any whitespace")
before parsing - ASCII space ` `, non-breaking space ` `, thin
space ` `, narrow no-break space ` `. Then parse exactly via BigInt (no float), as today.

**Rationale**: PrivatBank metals prices arrive as `"6 780.00"` with a (often non-ASCII) space
separator; the current `toMinorUnits` regex `^-?\d+(\.\d+)?$` rejects them (FR-005). Stripping
whitespace is harmless for bond prices (plain decimals, no spaces), so one shared converter stays
correct for both feeds. A comma (`,`) is NOT whitespace and stays rejected - the existing
`convert.test.mjs` asserts `'1,076.58'` throws, and that case is preserved. New unit tests cover
`"6 780.00" -> 678000` and a non-breaking-space (U+00A0) variant.

**Alternatives**: a metals-only parser - rejected; duplicates logic. Leaving conversion to the
backend - rejected; the scraper already submits integer minor units (009 contract), and the backend
ingest DTO expects `*Minor` longs.

## R5. Collection cadence and scheduling

**Decision**: A new GitHub Actions workflow `refresh-metal-prices.yml` on cron `0 7 * * *` (daily,
07:00 UTC = ~10:00 Kyiv, one-hour DST drift accepted), plus `workflow_dispatch` for manual runs. It
uses secret `METAL_INGEST_SECRET` and `BACKEND_BASE_URL`, mirroring the bond workflow.

**Rationale**: Spec assumption: metals publish daily (not business-day-only), so `* * *` not `1-5`
(the bond workflow's). Separate workflow keeps schedules, secrets, and failure isolation independent
(FR-008 distinct channel).

**No headless browser**: The metals endpoint is a plain public GET with no anti-bot handshake (FR-003,
spec assumption), so `scrape-metals.mjs` uses a plain `fetch` - no Playwright, no Chromium install
step. This makes the metals workflow lighter than the bond one.

## R6. Machine-to-machine ingest (distinct channel + secret)

**Decision**: Mirror 009 exactly with metals-specific names, all distinct from the bond channel
(FR-008): route `POST /api/v1/admin/metal-prices`, header `X-Metal-Ingest-Secret`, component
`MetalIngestAuth` (constant-time compare, fail-closed on blank), config `MetalsProperties`
(prefix `metals`, `metals.ingest.secret: ${METAL_INGEST_SECRET:}`), env `METAL_INGEST_SECRET`.
Add `/api/v1/admin/metal-prices` to `SecurityConfig.PUBLIC_POST` and gate
`GET /api/v1/metal-prices` to `hasRole("ADMIN")`.

**Rationale**: Reuses the proven, tested 009 security pattern; distinct secret/route/header keeps the
feeds independent and avoids overloading the bond endpoint.

**Scraper post helper**: Generalize `scraper/lib/post.mjs` `warmupAndPost` to accept the ingest path,
header name, AND the secret-env-name used in the "refusing to POST" error message as parameters, all
defaulting to the bond values so 009 stays behaviorally identical. (The current helper hardcodes
`'X-Bond-Ingest-Secret'`, the `INGEST_PATH` constant, and the literal `'BOND_INGEST_SECRET is not
set'` message - all three must be parameterized, not just the path/header.) Because `scraper/test/`
has no `post.test.mjs` today, changing this shared file is unguarded; add a small `post.test.mjs` (or
declare a static-only verification of the bond path) so the 009 regression is covered.

**Config registration**: `MetalsProperties` must be added to the explicit
`@EnableConfigurationProperties` list on `InvestGuideApplication` (the project does NOT use
`@ConfigurationPropertiesScan`, mirroring how `BondsProperties` is registered); without this the bean
won't bind and `MetalIngestAuth` won't construct.

**Date helper**: `toIsoDate` (DD.MM.YYYY -> ISO) lives inside `scrape-bonds.mjs`, not a shared lib. The
metals scraper needs the same conversion; either extract it to `scraper/lib/` for reuse or duplicate a
small local copy in `scrape-metals.mjs`. Prefer extraction to avoid drift.

## R7. Partial-feed and validation guards

**Decision**: Scraper-side `validateMetalBatch` requires **both** `GOLD` and `SILVER` to be present
(FR-015) and enforces a minimum record floor (`METALS_MIN_RECORDS`, default 20 - well below the
expected ~50 = 2 metals x 3 groups x 7-11 weights, above any truncated partial). Backend ingest
validates each record individually (metal in {GOLD,SILVER}, non-blank rateGroup/weightKey, positive
weightGrams, non-negative integer rates, ISO date), dropping bad records and counting accepted/rejected
(FR-010), exactly like the bond service.

**Rationale**: A pure count floor misses "one metal entirely absent" (silver alone could clear 20);
the explicit both-metals check closes that gap (spec Edge Cases). Per-record backend validation mirrors
009's `IngestBondRequest` bean-validation + per-record drop.

## R8. Option price field surfacing

**Decision**: Add two nullable fields to `InvestmentOption` (backend record + persisted shape +
frontend model + response): `metal` (`GOLD`/`SILVER`, null for non-metals) and
`metalPricePerGramMinor` (Long kopiykas/gram, null when not a grounded metals option). The frontend
results component renders a "price per gram" fact row only when present.

**Rationale**: The option already persists embedded in `SearchRequest` and renders identically from
history; adding nullable fields is backward compatible (old records read back null). Non-metals options
are unaffected (FR-020).

## Resolved unknowns summary

| Unknown | Resolution |
|---------|-----------|
| Which metal does a metals option refer to? | Model-emitted, server-validated `metal` enum (R1) |
| Which exact value is the price? | Sale rate, kopiykas/gram, primary group, smallest tier (R2) |
| How is one-record-per-combination guaranteed? | Deterministic composite `_id` upsert (R3) |
| How are `"6 780.00"` strings parsed? | Whitespace-stripping shared `toMinorUnits`, BigInt (R4) |
| Schedule / browser? | Daily cron, plain fetch, no Playwright (R5) |
| Ingest security? | Distinct route/header/secret, 009 pattern (R6) |
| Partial-feed guard? | Both-metals-present + count floor (R7) |
| How does the price reach the user? | Nullable option fields + conditional render (R8) |

No `NEEDS CLARIFICATION` markers remain.
