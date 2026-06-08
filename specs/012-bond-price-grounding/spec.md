# Feature Specification: Bond Price Grounding in Investment Options

**Feature Branch**: `012-bond-price-grounding`  
**Created**: 2026-06-08  
**Status**: Draft  
**Input**: User description: "Make the bond pipeline work like the metal pipeline: fetched + persisted bond prices should be used as the real price for an investment option when an appropriate bond option is presented to the user. Option A: per-ISIN — the model emits the specific bond ISIN, the server backfills that bond's exact stored value, and drops the option if the ISIN is unknown or unpriced."

## Context

Feature 009 already **collects** Ukrainian government/military bond quotes from PrivatBank and
**persists** them (the `bondPrices` store, one record per ISIN: sell/buy price in integer minor units
per 1000 face value, sell/buy yield, maturity, currency, quotation date), exposed **admin-only**.
What 009 does **not** do is use those stored values when a bond shows up as an investment option — a
`MILITARY_BOND`/`GOV_BOND` option's figures come from the catalog and the model, not the live data.

Feature 011 closed exactly this loop for precious metals (model emits a `GOLD`/`SILVER` discriminator;
the server backfills the exact stored price and drops the option if it cannot be grounded). This
feature does the same for bonds, **per-ISIN** (Option A): the model names the specific bond, the server
grounds its real values. This is the third stage (fetch -> persist -> **use as real price**) that bonds
are currently missing.

## Clarifications

### Session 2026-06-08

- Q: How is the specific bond (ISIN) for a bond option chosen? → A: List the currently stored bonds (ISIN + maturity + currency + indicative yield) to the model alongside the allowed providers; the model picks a real ISIN; the server validates it against the stored set and drops the option if it is missing/unknown. This keeps the ISIN as the model's editorial choice while making that choice valid (avoiding near-total drops).
- Q: Which exact stored values does a grounded bond option carry, and how do they relate to the existing expected-return range? → A: The bond's real sell yield REPLACES the option's expected-return range (becomes the authoritative return figure, a degenerate range where min == max == the stored sell yield), and the sell price (per 1000 face value, integer minor units) is added as a new bond field along with the chosen ISIN. Sell-side only (no buy price/yield surfaced in v1).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bond investment options show the exact current price and yield (Priority: P1)

A user runs the existing investment search that returns five investment options. When one of those
options is a government or military bond, the option carries the **exact current PrivatBank values for
that specific bond** (its real sell price and sell yield), rather than a model-estimated number or a
generic catalog range — so the user sees real, verifiable, up-to-date bond economics.

**Why this priority**: This is the entire point of collecting and persisting bond prices in feature
009. Without it, the stored data is admin-only and never reaches the user; grounding it in the options
the user actually sees is what makes the collection worthwhile.

**Independent Test**: With a current bond quote stored for a known ISIN, run a search whose results
include a bond option that names that ISIN, and confirm the option's price and yield equal the stored
exact values (not a model estimate). With no stored quote for the named ISIN, confirm the bond option
is not presented.

**Acceptance Scenarios**:

1. **Given** a current quote for a specific bond ISIN is stored and the existing generator presents a
   bond option that names that ISIN, **When** the search completes, **Then** the option's expected-
   return figure equals the stored sell yield (min == max == sell yield) and the option carries the
   stored sell price (minor units per 1000 face) and the ISIN, with no model-estimated figure used for
   them.
2. **Given** the generator presents a bond option whose named ISIN is not in the stored set (or the
   ISIN is missing/unparseable), **When** the search completes, **Then** that option is dropped
   server-side and not shown to the user; the rest of the search still returns.
3. **Given** an option is not a government/military bond, **When** the search completes, **Then** the
   bond-price grounding does not alter that option.
4. **Given** several bond options each name a different valid stored ISIN, **When** the search
   completes, **Then** each carries its own bond's exact values (no cross-contamination).

---

### User Story 2 - Administrator still reads raw bond prices unchanged (Priority: P3)

An administrator continues to read the full stored bond price set exactly as feature 009 provides it;
this feature does not change that capability, and does not expose any new raw bond-price endpoint to
end users.

**Why this priority**: Confirms the change is additive and does not weaken the existing access model;
it is a guardrail, not new user value, so it is the lowest priority.

**Independent Test**: The existing admin bond-price read returns the same data as before; no
non-administrator can read raw bond prices.

**Acceptance Scenarios**:

1. **Given** stored bond prices exist, **When** an administrator reads the bond prices, **Then** they
   receive the full set unchanged from feature 009 behavior.
2. **Given** a non-administrator, **When** they attempt to read raw bond prices, **Then** access is
   denied (no new public bond-price surface is introduced).

---

### Edge Cases

- **Unknown / hallucinated ISIN**: If the model names an ISIN that is not in the stored set, the option
  is dropped (not shown), never filled with an estimated value.
- **Known provider, missing ISIN field**: If a bond option omits the ISIN entirely, it is dropped (the
  server cannot ground it).
- **All bond options ungroundable**: If every surviving option is a bond that cannot be grounded, the
  result is a legitimate empty option set — the whole search MUST NOT fail, and the user MUST NOT be
  charged a corrective retry (the same non-throwing drop semantics feature 011 established).
- **Stale stored quote**: The grounded values reflect the latest stored quote; freshness is judged by
  the stored fetch time (as in 009). A stale-but-present quote is still used (not dropped).
- **Currency mismatch**: If the named ISIN's stored quote is in a different currency than the option's
  resolved currency, the option is dropped (never surface a price in the wrong currency). EUR-stored
  bonds are never listed to the model (no search requests EUR), so they are never grounded in v1.
- **Per-1000-face display**: The grounded sell price is quoted per 1000 face value (not the user's
  outlay); the user-facing label MUST say so explicitly to avoid being read as the cost to invest.
- **Mixed option set**: A non-bond option in the same result is never altered by bond grounding.
- **Multiple ISINs for one issuer**: The model selects a single ISIN per bond option; the server grounds
  that one. There is no aggregation across ISINs.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When the existing investment-options generator would present an option of category
  `MILITARY_BOND` or `GOV_BOND`, the system MUST set that option's grounded bond values from the exact
  current stored quote for the specific bond the option names, backfilled server-side, and MUST NOT use
  a model-estimated value for those grounded fields.
- **FR-002**: The specific bond MUST be identified by an ISIN supplied by the model on that option (a
  per-option discriminator), validated server-side against the stored bond set.
- **FR-003**: For a grounded bond option, the bond's stored **sell yield** MUST become the option's
  expected-return figure (replacing the model/catalog expected-return range with a degenerate range
  whose min and max both equal the stored sell yield), and the bond's stored **sell price** (integer
  minor units per 1000 face value) MUST be added as a new bond field together with the chosen ISIN.
  Only sell-side values are surfaced in v1 (buy price/buy yield are not shown).
- **FR-004**: When the generator would present a bond option but the named ISIN is missing,
  unparseable, not in the stored set, or has no stored price, the system MUST drop the option
  server-side (it is not shown to the user); it MUST NOT substitute an estimated value and MUST NOT
  fail the whole search.
- **FR-005**: The drop MUST be a non-throwing post-filter: a dropped bond option (which references a
  real in-catalog provider) MUST NOT trigger the "no valid options / hallucination" failure path, even
  if it empties the final option set (which then returns as a legitimate empty result).
- **FR-006**: The bond-price grounding MUST apply only to `MILITARY_BOND`/`GOV_BOND` options and MUST
  NOT alter any other option.
- **FR-007**: The grounding MUST read from the existing stored bond data (feature 009's `bondPrices`)
  and MUST NOT trigger a live fetch from the source.
- **FR-008**: The system MUST present the set of currently stored bonds (ISIN + maturity + currency +
  indicative yield) to the option generator alongside the allowed providers, so the model can choose a
  real ISIN for a bond option. The server MUST still validate the chosen ISIN against the stored set and
  drop the option on a missing/unknown ISIN (FR-004). The bond list MUST be assembled within the
  existing input-token budget (subject to the same deterministic truncation the provider list uses).
- **FR-009**: Raw bond-price read access MUST remain administrator-only (unchanged from feature 009);
  end users MUST only see the grounded option, never a raw bond-price endpoint.
- **FR-010**: The model MUST contribute only the ISIN selection for a bond option; the price and yield
  values MUST always be the server-grounded stored values, never model-supplied. In particular, any
  expected-return range the model emits for a grounded bond option MUST be discarded and replaced by the
  stored sell yield (FR-003).
- **FR-011**: The server MUST guarantee currency consistency: a bond option MUST only be grounded with a
  stored quote whose currency matches the option's resolved currency. If the named ISIN's stored
  currency does not match, the option MUST be dropped (the server is the guarantee — the prompt list is
  only steering). The search request currency is one of UAH or USD; bonds stored in a currency that no
  search can request (e.g. EUR) are simply never listed and never grounded in v1.
- **FR-012**: When a grounded bond option's return figure is a single value (sell yield, where the
  expected-return min equals max), the user-facing display MUST present it as one number (not a
  degenerate "X-X" range).

### Key Entities *(include if feature involves data)*

- **Bond Price Record** (existing, feature 009): the stored latest quote for one bond, keyed by ISIN,
  carrying sell/buy price (integer minor units per 1000 face), sell/buy yield, maturity, currency,
  quotation date, fetched-at. This feature consumes it read-only; it adds no new storage.
- **Investment Option** (existing): for a grounded `MILITARY_BOND`/`GOV_BOND` option, its expected-
  return figure is set to the bond's stored sell yield (min == max == sell yield), and it gains two new
  bond fields: the chosen ISIN and the bond's stored sell price (integer minor units per 1000 face
  value). The new fields are null/absent for non-bond options and for legacy persisted searches.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of presented bond options carry the exact stored values for their named ISIN; 0%
  carry a model-estimated bond price or yield in the grounded fields. A model-supplied price or
  expected-return range on a grounded bond option is always overwritten by the stored values.
- **SC-002**: When the named ISIN has no stored quote, 100% of those would-be bond options are dropped
  (none shown with an estimated or blank grounded value), and the rest of the search still returns.
- **SC-003**: An all-bond-ungroundable result never fails the search and never charges a corrective
  retry (the search returns an empty option set instead).
- **SC-004**: Non-bond options are byte-for-byte unaffected by the grounding (no grounded bond fields
  set on them).
- **SC-005**: Raw bond-price reads remain administrator-only; 100% of non-administrator raw-read
  attempts are denied (unchanged from feature 009).
- **SC-006**: For a verified sample, each grounded option's values match the stored record for its ISIN
  exactly (no rounding drift; the sell-price minor-unit integer is copied as-is).
- **SC-007**: 100% of grounded bond options whose stored quote currency differs from the option's
  resolved currency are dropped (no cross-currency price is ever shown).

## Assumptions

- **Reuse of the metals pattern**: The grounding reuses the feature 011 mechanism — a model-emitted
  per-option discriminator, server-side exact-value backfill, and a two-stage non-throwing drop-on-
  missing applied after catalog grounding. No new fetch/persist/ingest is built; feature 009's
  collection and storage are reused as-is.
- **Bond identity is the model's editorial choice; values are grounded**: Choosing which bond to
  recommend (its ISIN) is the model's job, exactly like choosing the instrument text; the price and
  yield are always server-grounded, so the model can never invent bond economics.
- **Grounded fields (resolved, FR-003)**: the option's expected-return figure is set to the bond's
  stored sell yield (degenerate min == max range), and the bond's stored sell price (minor units per
  1000 face) plus the chosen ISIN are added as new fields; sell-side only in v1.
- **Discriminator delivery (resolved, FR-008)**: the currently stored bond identifiers (ISIN +
  maturity + currency + indicative yield) are listed to the model alongside the allowed providers so it
  can pick a real ISIN, kept within the existing input-token budget via the same deterministic
  truncation the provider list uses.
- **Latest-quote-only**: only the latest stored quote per ISIN is used (no history), consistent with
  feature 009.
- **No model/option-selection change**: this feature does not change which options the generator
  chooses to present, only how a bond option's values are grounded.

## Out of Scope (Non-Goals)

- Any change to bond collection, ingest, or storage (feature 009 is reused unchanged).
- A user-facing raw bond-price endpoint (reads stay administrator-only).
- Aggregating across multiple ISINs for a single bond option (Option B / category-aggregate is
  explicitly not chosen).
- Historical bond price/yield series or charting.
- Changing how the generator decides whether to include a bond at all.

## Dependencies

- **Feature 011 grounding pattern**: this feature reuses the two-stage, non-throwing grounding in the
  advisor output enforcement that feature 011 introduced; feature 011 MUST be merged first (this work
  extends the same enforcement point for bonds).
- **Feature 009 bond storage**: the `bondPrices` store and its ISIN-keyed records are the data source.
- The existing investment-options generator (the LLM-backed search with server-side catalog grounding)
  and its `MILITARY_BOND`/`GOV_BOND` option categories.
- The existing administrator role/authorization for raw bond-price reads.
